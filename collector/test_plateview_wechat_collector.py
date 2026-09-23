import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest
import urllib.error
import urllib.request
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).with_name("plateview_wechat_collector.py")
SPEC = importlib.util.spec_from_file_location("plateview_wechat_collector", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class CollectorTest(unittest.TestCase):
    def create_collector(self, directory):
        return MODULE.Collector("http://127.0.0.1:8080", "测试令牌", pathlib.Path(directory) / "state.json")

    def test_server_failure_does_not_advance_message_cursor(self):
        with tempfile.TemporaryDirectory() as directory:
            collector = self.create_collector(directory)
            message = {"local_id": "2", "timestamp": 200, "content": "【单号】001"}
            collector._wx_json = mock.Mock(side_effect=[{"messages": [message], "meta": {"status": "ok"}}])
            collector._request_json = mock.Mock(side_effect=[None, OSError("服务不可用"), None])

            with self.assertRaises(OSError):
                collector._sync_source("群", "测试群")

            self.assertNotIn("message_timestamp", collector.state["群"])

    def test_successful_upload_advances_cursor_after_server_confirmation(self):
        with tempfile.TemporaryDirectory() as directory:
            collector = self.create_collector(directory)
            message = {"local_id": "2", "timestamp": 200, "content": "【单号】001"}
            collector._wx_json = mock.Mock(side_effect=[
                {"messages": [message], "meta": {"status": "ok"}},
                {"messages": [], "meta": {"status": "ok"}},
                {"messages": [], "meta": {"status": "ok"}},
            ])
            collector._request_json = mock.Mock(return_value={})

            collector._sync_source("群", "测试群")

            self.assertEqual(200, collector.state["群"]["message_timestamp"])
            self.assertEqual("2", collector.state["群"]["message_id"])
            self.assertTrue((pathlib.Path(directory) / "state.json").is_file())

    def test_message_batch_contains_stable_id_and_digest(self):
        with tempfile.TemporaryDirectory() as directory:
            collector = self.create_collector(directory)
            message = {"local_id": "2", "timestamp": 200, "content": "【单号】001"}
            collector._wx_json = mock.Mock(side_effect=[
                {"messages": [message], "meta": {"status": "ok"}},
                {"messages": [], "meta": {"status": "ok"}},
                {"messages": [], "meta": {"status": "ok"}},
            ])
            collector._request_json = mock.Mock(return_value={"status": "ACCEPTED"})

            collector._sync_source("群", "测试群")

            body = next(call.args[2] for call in collector._request_json.call_args_list if call.args[1] == "/internal/wechat/messages/batch")
            self.assertTrue(body["batchId"])
            self.assertTrue(body["syncRunId"])
            self.assertEqual(1, body["messageCount"])
            self.assertEqual(64, len(body["batchSha256"]))

    def test_stale_shard_status_never_advances_cursor(self):
        with tempfile.TemporaryDirectory() as directory:
            collector = self.create_collector(directory)
            collector._wx_json = mock.Mock(return_value={
                "messages": [{"local_id": "2", "timestamp": 200, "content": "【单号】001"}],
                "meta": {"status": "possibly_stale_unknown_shards"},
            })
            collector._request_json = mock.Mock(return_value={})

            collector._sync_source("群", "测试群")

            self.assertNotIn("message_timestamp", collector.state["群"])

    def test_image_decryption_failure_uploads_metadata_only(self):
        with tempfile.TemporaryDirectory() as directory:
            collector = self.create_collector(directory)
            collector._run = mock.Mock(side_effect=subprocess.CalledProcessError(1, ["wx", "extract"]))
            collector._request_multipart = mock.Mock()

            collector._upload_image(
                "20546602068@chatroom",
                "2026车单子接收群",
                {"attachment_id": "image-1", "timestamp": 200, "sender_username": "wxid_test"},
            )

            uploaded_files = collector._request_multipart.call_args.args[2]
            self.assertEqual({}, uploaded_files)

    def test_hevc_image_is_converted_before_derivatives_and_upload(self):
        with tempfile.TemporaryDirectory() as directory:
            collector = self.create_collector(directory)

            def run(command):
                if command[1] == "extract":
                    pathlib.Path(command[4]).write_bytes(b"wechat-hevc-stream")
                    return subprocess.CompletedProcess(command, 0, json.dumps({"format": "hevc", "output": command[4]}), "")
                pathlib.Path(command[-1]).write_bytes(b"\xff\xd8\xffconverted-image")
                return subprocess.CompletedProcess(command, 0, "", "")

            collector._run = mock.Mock(side_effect=run)
            collector._create_derivatives = mock.Mock(return_value={})
            collector._request_multipart = mock.Mock()

            uploaded = collector._upload_image(
                "20546602068@chatroom",
                "2026车单子接收群",
                {"attachment_id": "image-1", "local_id": "21", "timestamp": 200},
            )

            self.assertTrue(uploaded)
            fields = collector._request_multipart.call_args.args[1]
            files = collector._request_multipart.call_args.args[2]
            self.assertEqual("image/jpeg", fields["originalContentType"])
            self.assertEqual("original.jpg", files["original"].name)
            self.assertEqual("ffmpeg", collector._run.call_args_list[1].args[0][0])

    def test_thumbnail_is_uploaded_only_as_thumbnail_and_kept_for_retry(self):
        with tempfile.TemporaryDirectory() as directory:
            collector = self.create_collector(directory)

            def run(command):
                pathlib.Path(command[4]).write_bytes(b"\xff\xd8\xffthumbnail")
                report = {
                    "format": "jpg",
                    "output": command[4],
                    "resource_quality": "THUMBNAIL",
                }
                return subprocess.CompletedProcess(command, 0, json.dumps(report), "")

            thumbnail = pathlib.Path(directory) / "thumbnail.webp"
            thumbnail.write_bytes(b"webp-thumbnail")
            collector._run = mock.Mock(side_effect=run)
            collector._create_derivatives = mock.Mock(return_value={"thumbnail": thumbnail})
            collector._request_multipart = mock.Mock()

            completed = collector._upload_image(
                "20546602068@chatroom",
                "2026车单子接收群",
                {"attachment_id": "image-1", "local_id": "21", "timestamp": 200},
            )

            self.assertFalse(completed)
            fields = collector._request_multipart.call_args.args[1]
            files = collector._request_multipart.call_args.args[2]
            self.assertEqual("THUMBNAIL", fields["sourceQuality"])
            self.assertNotIn("original", files)
            self.assertEqual(thumbnail, files["thumbnail"])

    @mock.patch.object(MODULE.time, "time", return_value=1_000)
    def test_failed_image_is_persisted_and_retried_outside_query_window(self, _):
        with tempfile.TemporaryDirectory() as directory:
            collector = self.create_collector(directory)
            attachment = {"attachment_id": "opaque-1", "local_id": "21", "timestamp": 200}
            collector._wx_json = mock.Mock(return_value={"messages": [attachment], "meta": {"status": "ok"}})
            collector._upload_image = mock.Mock(return_value=False)

            collector._sync_images("群", "测试群", collector.state.setdefault("群", {}), False)

            retries = collector.state["群"]["image_retry_items"]
            self.assertEqual("opaque-1", retries[0]["attachment_id"])
            self.assertEqual(1_060, retries[0]["next_retry_at"])

            retries[0]["next_retry_at"] = 0
            collector._wx_json = mock.Mock(return_value={"messages": [], "meta": {"status": "ok"}})
            collector._upload_image = mock.Mock(return_value=True)
            collector._sync_images("群", "测试群", collector.state["群"], False)

            collector._upload_image.assert_called_once()
            self.assertEqual([], collector.state["群"]["image_retry_items"])

    def test_pdf_file_is_located_by_message_month_and_name(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            target = root / "account" / "msg" / "file" / "2026-09" / "0920019.pdf"
            target.parent.mkdir(parents=True)
            target.write_bytes(b"%PDF-1.7\n")
            collector = MODULE.Collector("http://127.0.0.1:8080", "测试令牌", root / "state.json", wechat_files_root=root)

            located = collector._locate_pdf({"timestamp": 1789892110, "content": "[文件] 0920019.pdf (148.7 KB, pdf)"})

            self.assertEqual(target, located)

    def test_pdf_upload_carries_source_message_id_for_preview_linking(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            original = root / "车辆申请.pdf"
            original.write_bytes(b"%PDF-1.7\n")
            collector = MODULE.Collector("http://127.0.0.1:8080", "测试令牌", root / "state.json", wechat_files_root=root)
            collector._locate_pdf = mock.Mock(return_value=original)
            collector._create_pdf_derivatives = mock.Mock(return_value={})
            collector._request_multipart = mock.Mock()

            collector._upload_pdf(
                "群标识",
                "测试群",
                {"local_id": "387", "timestamp": 1786875133, "content": "[文件] 车辆申请.pdf (612.8 KB, pdf)"},
            )

            fields = collector._request_multipart.call_args.args[1]
            self.assertEqual("387", fields["localMessageId"])
            self.assertEqual("pdf-387", fields["localAttachmentId"])

    def test_duplicate_pdf_candidates_are_not_guessed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for account in ("a", "b"):
                target = root / account / "msg" / "file" / "2026-09" / "0920019.pdf"
                target.parent.mkdir(parents=True)
                target.write_bytes(b"%PDF-1.7\n")
            collector = MODULE.Collector("http://127.0.0.1:8080", "测试令牌", root / "state.json", wechat_files_root=root)

            self.assertIsNone(collector._locate_pdf({"timestamp": 1789892110, "content": "[文件] 0920019.pdf (9 B, pdf)"}))

    @mock.patch.object(MODULE.time, "sleep")
    @mock.patch.object(urllib.request, "urlopen")
    def test_transient_network_failure_is_retried(self, urlopen, sleep):
        collector = self.create_collector(tempfile.gettempdir())
        response = mock.MagicMock()
        urlopen.side_effect = [urllib.error.URLError(TimeoutError()), response]

        actual = collector._urlopen_with_retry(mock.Mock(), timeout=30)

        self.assertIs(response, actual)
        self.assertEqual(2, urlopen.call_count)
        sleep.assert_called_once_with(1)

    @mock.patch.object(MODULE.time, "sleep")
    @mock.patch.object(urllib.request, "urlopen")
    def test_client_error_is_not_retried(self, urlopen, sleep):
        collector = self.create_collector(tempfile.gettempdir())
        error = urllib.error.HTTPError("https://example.invalid", 401, "未授权", {}, None)
        urlopen.side_effect = error

        with self.assertRaises(urllib.error.HTTPError):
            collector._urlopen_with_retry(mock.Mock(), timeout=30)

        self.assertEqual(1, urlopen.call_count)
        sleep.assert_not_called()


if __name__ == "__main__":
    unittest.main()

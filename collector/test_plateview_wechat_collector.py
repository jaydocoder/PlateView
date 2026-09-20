import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest
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

    def test_pdf_file_is_located_by_message_month_and_name(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            target = root / "account" / "msg" / "file" / "2026-09" / "0920019.pdf"
            target.parent.mkdir(parents=True)
            target.write_bytes(b"%PDF-1.7\n")
            collector = MODULE.Collector("http://127.0.0.1:8080", "测试令牌", root / "state.json", wechat_files_root=root)

            located = collector._locate_pdf({"timestamp": 1789892110, "content": "[文件] 0920019.pdf (148.7 KB, pdf)"})

            self.assertEqual(target, located)

    def test_duplicate_pdf_candidates_are_not_guessed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for account in ("a", "b"):
                target = root / account / "msg" / "file" / "2026-09" / "0920019.pdf"
                target.parent.mkdir(parents=True)
                target.write_bytes(b"%PDF-1.7\n")
            collector = MODULE.Collector("http://127.0.0.1:8080", "测试令牌", root / "state.json", wechat_files_root=root)

            self.assertIsNone(collector._locate_pdf({"timestamp": 1789892110, "content": "[文件] 0920019.pdf (9 B, pdf)"}))


if __name__ == "__main__":
    unittest.main()

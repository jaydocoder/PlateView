#!/usr/bin/env python3
import argparse
import datetime as dt
import hashlib
import json
import logging
import mimetypes
import os
import pathlib
import re
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

from PIL import Image, ImageOps


SOURCES = {
    "44367002464@chatroom": "票务中心工作群",
    "31463879194@chatroom": "贾登峪车道口",
    "20546602068@chatroom": "2026车单子接收群",
}
SCAN_INTERVAL_SECONDS = 10
RESCAN_INTERVAL_SECONDS = 60 * 60
OVERLAP_SECONDS = 24 * 60 * 60
REGULAR_OVERLAP_SECONDS = 5 * 60
BATCH_SIZE = 200
MAX_HISTORY = 100_000


class Collector:
    def __init__(self, server_url, token, state_path, wx_command="wx", wechat_files_root="~/文档/xwechat_files"):
        self.server_url = server_url.rstrip("/")
        self.token = token
        self.state_path = pathlib.Path(state_path).expanduser()
        self.wx_command = wx_command
        self.wechat_files_root = pathlib.Path(wechat_files_root).expanduser()
        self.state = self._load_state()

    def run_forever(self):
        delay = SCAN_INTERVAL_SECONDS
        while True:
            try:
                self.run_once()
                delay = SCAN_INTERVAL_SECONDS
            except Exception as error:
                logging.error("采集轮次失败，错误类型=%s", type(error).__name__)
                delay = min(max(delay * 2, 20), 15 * 60)
            time.sleep(delay)

    def run_once(self):
        failures = []
        for source_key, source_name in SOURCES.items():
            try:
                self._sync_source(source_key, source_name)
            except Exception as error:
                failures.append(error)
                logging.error("群同步失败，群=%s，错误类型=%s", source_name, type(error).__name__)
                try:
                    self._heartbeat(source_key, source_name, "UPLOAD_FAILED", None, 0, type(error).__name__)
                except Exception:
                    logging.error("同步异常状态上报失败，群=%s", source_name)
        if failures:
            raise failures[-1]

    def _sync_source(self, source_key, source_name):
        source_state = self.state.setdefault(source_key, {})
        now = int(time.time())
        full_rescan = now - int(source_state.get("last_rescan_at", 0)) >= RESCAN_INTERVAL_SECONDS
        since = self._since_date(source_state.get("message_timestamp"), full_rescan)
        payload = self._wx_json(["history", source_key, "--json", "--since", since, "-n", str(MAX_HISTORY)])
        meta = payload.get("meta") or {}
        status = meta.get("status", "ok")
        if status in {"possibly_stale_unknown_shards", "possibly_stale"}:
            self._heartbeat(source_key, source_name, "KEY_MISSING", None, 0, status)
            logging.warning("微信数据可能不完整，群=%s，状态=%s", source_name, status)
            return

        messages = sorted(payload.get("messages") or [], key=self._message_position)
        cursor = (int(source_state.get("message_timestamp", 0)), str(source_state.get("message_id", "")))
        pending = [
            message for message in messages
            if self._message_text(message) and (full_rescan or self._message_position(message) > cursor)
        ]
        self._heartbeat(source_key, source_name, "CATCHING_UP" if pending else "HEALTHY", self._latest_timestamp(messages), len(pending), None)
        for batch in chunks(pending, BATCH_SIZE):
            body = {
                "sourceKey": source_key,
                "sourceName": source_name,
                "messages": [self._message_request(source_key, item) for item in batch],
            }
            self._request_json("POST", "/internal/wechat/messages/batch", body)
            last = batch[-1]
            timestamp, local_id = max(cursor, self._message_position(last))
            source_state["message_timestamp"] = timestamp
            source_state["message_id"] = local_id
            cursor = timestamp, local_id
            self._save_state()
        self._sync_images(source_key, source_name, source_state, full_rescan)
        self._sync_files(source_key, source_name, source_state, full_rescan)
        if full_rescan:
            source_state["last_rescan_at"] = now
            self._save_state()
        self._heartbeat(source_key, source_name, "HEALTHY", self._latest_timestamp(messages), 0, None)
        logging.info("群同步完成，群=%s，新增候选=%d", source_name, len(pending))

    def _sync_images(self, source_key, source_name, source_state, full_rescan):
        since = self._since_date(source_state.get("image_timestamp"), full_rescan)
        payload = self._wx_json(["attachments", source_key, "--kind", "image", "--json", "--since", since, "-n", str(MAX_HISTORY)])
        status = (payload.get("meta") or {}).get("status", "ok")
        if status in {"possibly_stale_unknown_shards", "possibly_stale"}:
            raise RuntimeError("微信图片数据可能不完整")
        attachments = sorted(payload.get("attachments") or payload.get("messages") or [], key=self._message_position)
        cursor = (int(source_state.get("image_timestamp", 0)), str(source_state.get("image_id", "")))
        retry_ids = set(source_state.get("image_retry_ids") or [])
        pending = [
            item for item in attachments
            if full_rescan
            or self._message_position(item) > cursor
            or str(item.get("local_id") or item.get("message_id") or "") in retry_ids
        ]
        for attachment in pending:
            uploaded = self._upload_image(source_key, source_name, attachment)
            timestamp, local_id = max(cursor, self._message_position(attachment))
            retry_ids = set(source_state.get("image_retry_ids") or [])
            retry_key = str(attachment.get("local_id") or attachment.get("message_id") or "")
            if uploaded:
                retry_ids.discard(retry_key)
            elif retry_key:
                retry_ids.add(retry_key)
            source_state["image_timestamp"] = timestamp
            source_state["image_id"] = local_id
            cursor = timestamp, local_id
            source_state["image_retry_ids"] = sorted(retry_ids)[-5000:]
            self._save_state()

    def _upload_image(self, source_key, source_name, attachment):
        attachment_id = str(attachment.get("attachment_id") or "")
        if not attachment_id:
            return
        fields = {
            "sourceKey": source_key,
            "sourceName": source_name,
            "localAttachmentId": attachment_id,
            "localMessageId": str(attachment.get("local_id") or attachment.get("message_id") or ""),
            "senderUsername": str(attachment.get("sender_username") or ""),
            "senderDisplay": str(attachment.get("sender") or attachment.get("sender_contact_display") or ""),
            "sentAt": iso_time(self._timestamp(attachment)),
        }
        with tempfile.TemporaryDirectory(prefix="plateview-wechat-") as temporary_directory:
            original = pathlib.Path(temporary_directory) / "original"
            try:
                completed = self._run([self.wx_command, "extract", attachment_id, "-o", str(original), "--overwrite", "--json"])
                if not original.is_file():
                    report = json.loads(completed.stdout) if completed.stdout.strip().startswith("{") else {}
                    reported_output = pathlib.Path(str(report.get("output") or ""))
                    if reported_output.is_file() and reported_output.parent == pathlib.Path(temporary_directory):
                        original = reported_output
                if not original.is_file():
                    extracted = [path for path in pathlib.Path(temporary_directory).iterdir() if path.is_file()]
                    if len(extracted) != 1:
                        raise FileNotFoundError("微信图片解密后未找到唯一输出文件")
                    original = extracted[0]
                fields["sha256"] = file_sha256(original)
                fields["originalContentType"] = mimetypes.guess_type(original.name)[0] or detect_image_type(original)
                files = {"original": original}
                files.update(self._create_derivatives(original, pathlib.Path(temporary_directory)))
                self._request_multipart("/internal/wechat/images", fields, files)
                return True
            except subprocess.CalledProcessError:
                self._request_multipart("/internal/wechat/images", fields, {})
                return False

    def _sync_files(self, source_key, source_name, source_state, full_rescan):
        since = self._since_date(source_state.get("file_timestamp"), full_rescan)
        payload = self._wx_json(["history", source_key, "--type", "file", "--json", "--since", since, "-n", str(MAX_HISTORY)])
        status = (payload.get("meta") or {}).get("status", "ok")
        if status in {"possibly_stale_unknown_shards", "possibly_stale"}:
            raise RuntimeError("微信文件数据可能不完整")
        messages = sorted(payload.get("messages") or [], key=self._message_position)
        cursor = (int(source_state.get("file_timestamp", 0)), str(source_state.get("file_id", "")))
        pending = [
            item for item in messages
            if self._pdf_file_name(item) and (full_rescan or self._message_position(item) > cursor)
        ]
        for message in pending:
            self._upload_pdf(source_key, source_name, message)
            timestamp, local_id = max(cursor, self._message_position(message))
            source_state["file_timestamp"] = timestamp
            source_state["file_id"] = local_id
            cursor = timestamp, local_id
            self._save_state()

    def _upload_pdf(self, source_key, source_name, message):
        file_name = self._pdf_file_name(message)
        if not file_name:
            return
        local_id = str(message.get("local_id") or message.get("id") or "")
        fields = {
            "sourceKey": source_key,
            "sourceName": source_name,
            "localAttachmentId": f"pdf-{local_id}",
            "senderUsername": str(message.get("sender_username") or ""),
            "senderDisplay": str(message.get("sender") or message.get("sender_contact_display") or ""),
            "sentAt": iso_time(self._timestamp(message)),
            "attachmentKind": "PDF",
            "fileName": file_name,
            "originalContentType": "application/pdf",
        }
        original = self._locate_pdf(message)
        if original is None:
            self._request_multipart("/internal/wechat/attachments", fields, {})
            return
        fields["sha256"] = file_sha256(original)
        with tempfile.TemporaryDirectory(prefix="plateview-wechat-pdf-") as temporary_directory:
            directory = pathlib.Path(temporary_directory)
            files = {"original": original}
            files.update(self._create_pdf_derivatives(original, directory, fields))
            self._request_multipart("/internal/wechat/attachments", fields, files)

    def _pdf_file_name(self, message):
        content = self._message_text(message)
        match = re.search(r"\[文件\]\s*(.+?\.pdf)\s*(?:\(|$)", content, re.IGNORECASE)
        return pathlib.Path(match.group(1)).name if match else None

    def _locate_pdf(self, message):
        file_name = self._pdf_file_name(message)
        if not file_name or not self.wechat_files_root.is_dir():
            return None
        month = dt.datetime.fromtimestamp(self._timestamp(message), dt.timezone(dt.timedelta(hours=8))).strftime("%Y-%m")
        candidates = [
            path for path in self.wechat_files_root.glob(f"*/msg/file/{month}/{file_name}")
            if path.is_file()
        ]
        return candidates[0] if len(candidates) == 1 else None

    def _create_pdf_derivatives(self, original, directory, fields):
        try:
            info = self._run(["pdfinfo", str(original)]).stdout
            page_match = re.search(r"^Pages:\s*(\d+)", info, re.MULTILINE)
            if page_match:
                fields["pageCount"] = page_match.group(1)
            page = directory / "pdf-page"
            self._run(["pdftoppm", "-f", "1", "-l", "1", "-singlefile", "-scale-to", "1600", "-png", str(original), str(page)])
            rendered = page.with_suffix(".png")
            if not rendered.is_file():
                return {}
            return self._create_derivatives(rendered, directory)
        except (OSError, subprocess.CalledProcessError):
            logging.warning("PDF预览生成失败，文件摘要=%s", hashlib.sha256(original.name.encode()).hexdigest()[:12])
            return {}

    def _create_derivatives(self, original, directory):
        try:
            with Image.open(original) as source:
                source = ImageOps.exif_transpose(source)
                if source.mode not in {"RGB", "RGBA"}:
                    source = source.convert("RGB")
                result = {}
                for name, longest_edge, quality in (("thumbnail", 360, 74), ("preview", 1600, 84)):
                    image = source.copy()
                    image.thumbnail((longest_edge, longest_edge), Image.Resampling.LANCZOS)
                    target = directory / f"{name}.webp"
                    image.save(target, "WEBP", quality=quality, method=4)
                    result[name] = target
                return result
        except (OSError, ValueError):
            logging.warning("图片衍生图生成失败，附件标识摘要=%s", hashlib.sha256(str(original).encode()).hexdigest()[:12])
            return {}

    def _message_request(self, source_key, message):
        local_id = str(message.get("local_id") or message.get("id") or message.get("msg_id") or "")
        raw_content = self._message_text(message)
        fingerprint = hashlib.sha256(f"{source_key}\0{local_id}\0{raw_content}".encode()).hexdigest()
        return {
            "localMessageId": local_id or fingerprint,
            "senderUsername": message.get("sender_username"),
            "senderDisplay": message.get("sender_contact_display") or message.get("sender"),
            "senderGroupNickname": message.get("sender_group_nickname"),
            "rawContent": raw_content,
            "sentAt": iso_time(self._timestamp(message)),
            "contentFingerprint": fingerprint,
        }

    def _heartbeat(self, source_key, source_name, status, latest_timestamp, backlog_count, error_code):
        self._request_json(
            "POST",
            "/internal/wechat/heartbeat",
            {
                "sourceKey": source_key,
                "sourceName": source_name,
                "status": status,
                "latestMessageAt": iso_time(latest_timestamp) if latest_timestamp else None,
                "backlogCount": backlog_count,
                "errorCode": error_code,
            },
        )

    def _wx_json(self, arguments):
        completed = self._run([self.wx_command, *arguments])
        return json.loads(completed.stdout)

    def _run(self, command):
        return subprocess.run(command, check=True, capture_output=True, text=True, timeout=180)

    def _request_json(self, method, path, body):
        data = json.dumps(body, ensure_ascii=False).encode()
        request = urllib.request.Request(
            self.server_url + path,
            data=data,
            method=method,
            headers={"Content-Type": "application/json", "X-PlateView-Collector-Token": self.token},
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            content = response.read()
            return json.loads(content) if content else None

    def _request_multipart(self, path, fields, files):
        boundary = "----PlateView" + uuid.uuid4().hex
        body = bytearray()
        for name, value in fields.items():
            if value is None:
                continue
            body.extend(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{value}\r\n".encode())
        for name, file_path in files.items():
            content_type = mimetypes.guess_type(file_path.name)[0] or detect_image_type(file_path)
            body.extend(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"{file_path.name}\"\r\nContent-Type: {content_type}\r\n\r\n".encode())
            body.extend(file_path.read_bytes())
            body.extend(b"\r\n")
        body.extend(f"--{boundary}--\r\n".encode())
        request = urllib.request.Request(
            self.server_url + path,
            data=bytes(body),
            method="POST",
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}", "X-PlateView-Collector-Token": self.token},
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            response.read()

    def _load_state(self):
        if not self.state_path.is_file():
            return {}
        return json.loads(self.state_path.read_text(encoding="utf-8"))

    def _save_state(self):
        self.state_path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.state_path.with_suffix(".tmp")
        temporary.write_text(json.dumps(self.state, ensure_ascii=False, indent=2), encoding="utf-8")
        temporary.replace(self.state_path)

    def _since_date(self, timestamp, full_rescan=False):
        if not timestamp:
            return "2000-01-01"
        overlap = OVERLAP_SECONDS if full_rescan else REGULAR_OVERLAP_SECONDS
        return dt.datetime.fromtimestamp(max(0, int(timestamp) - overlap), dt.timezone.utc).date().isoformat()

    def _message_position(self, message):
        return self._timestamp(message), str(message.get("local_id") or message.get("attachment_id") or message.get("id") or "")

    def _timestamp(self, message):
        value = message.get("timestamp") or message.get("time") or message.get("create_time") or 0
        if isinstance(value, str) and not value.isdigit():
            return int(dt.datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp())
        return int(value)

    def _message_text(self, message):
        return str(message.get("content") or message.get("text") or message.get("message") or "").strip()

    def _latest_timestamp(self, messages):
        return max((self._timestamp(item) for item in messages), default=0)


def chunks(items, size):
    for index in range(0, len(items), size):
        yield items[index:index + size]


def iso_time(timestamp):
    return dt.datetime.fromtimestamp(int(timestamp), dt.timezone.utc).isoformat().replace("+00:00", "Z")


def file_sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def detect_image_type(path):
    header = path.read_bytes()[:16]
    if header.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if header.startswith(b"\x89PNG"):
        return "image/png"
    if header.startswith((b"GIF87a", b"GIF89a")):
        return "image/gif"
    if header.startswith(b"RIFF") and header[8:12] == b"WEBP":
        return "image/webp"
    return "application/octet-stream"


def main():
    parser = argparse.ArgumentParser(description="PlateView微信车单后台采集器")
    parser.add_argument("--once", action="store_true", help="只执行一轮同步")
    arguments = parser.parse_args()
    server_url = os.environ.get("PLATEVIEW_SERVER_URL", "").strip()
    token = os.environ.get("PLATEVIEW_COLLECTOR_TOKEN", "").strip()
    if not server_url or not token:
        raise SystemExit("必须配置PLATEVIEW_SERVER_URL和PLATEVIEW_COLLECTOR_TOKEN")
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    collector = Collector(
        server_url=server_url,
        token=token,
        state_path=os.environ.get("PLATEVIEW_COLLECTOR_STATE", "~/.local/state/plateview/wechat-collector.json"),
        wx_command=os.environ.get("WX_COMMAND", "wx"),
        wechat_files_root=os.environ.get("WECHAT_FILES_ROOT", "~/文档/xwechat_files"),
    )
    if arguments.once:
        collector.run_once()
    else:
        collector.run_forever()


if __name__ == "__main__":
    main()

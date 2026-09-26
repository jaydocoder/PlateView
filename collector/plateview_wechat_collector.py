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
RECONCILE_INTERVAL_SECONDS = 6 * 60 * 60
OVERLAP_SECONDS = 24 * 60 * 60
REGULAR_OVERLAP_SECONDS = 5 * 60
BATCH_SIZE = 200
MAX_HISTORY = 100_000
REQUEST_RETRY_DELAYS = (1, 3, 8)
IMAGE_RETRY_BASE_SECONDS = 60
IMAGE_RETRY_MAX_SECONDS = 60 * 60
MAX_IMAGE_RETRY_ITEMS = 5_000
MAX_ATTACHMENT_RETRY_ITEMS = 5_000


class RebuildInProgress(RuntimeError):
    """服务端正在重构微信数据，采集器必须暂停写入。"""


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
            except RebuildInProgress:
                logging.info("服务端微信数据正在重构，采集器暂停上传")
                delay = min(max(delay * 2, 30), 15 * 60)
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
        self._refresh_rebuild_state(source_state)
        sync_run_id = self.state.setdefault("_sync_run_id", str(uuid.uuid4()))
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
            requests = [self._message_request(source_key, item) for item in batch]
            batch_digest = self._batch_digest(requests)
            batch_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"{source_key}:{requests[0]['localMessageId']}:{requests[-1]['localMessageId']}:{batch_digest}"))
            body = {
                "batchId": batch_id,
                "syncRunId": sync_run_id,
                "sourceKey": source_key,
                "sourceName": source_name,
                "fromTimestamp": cursor[0],
                "fromLocalMessageId": cursor[1],
                "toTimestamp": int(self._timestamp(batch[-1])),
                "toLocalMessageId": requests[-1]["localMessageId"],
                "messageCount": len(requests),
                "batchSha256": batch_digest,
                "messages": requests,
            }
            response = self._request_json("POST", "/internal/wechat/messages/batch", body) or {}
            if response.get("status", "ACCEPTED") != "ACCEPTED":
                raise RuntimeError("服务端未确认微信消息批次")
            last = batch[-1]
            timestamp, local_id = max(cursor, self._message_position(last))
            source_state["message_timestamp"] = timestamp
            source_state["message_id"] = local_id
            cursor = timestamp, local_id
            self._save_state()
        self._reconcile_recent(source_key, source_name, source_state, messages, now)
        self._sync_images(source_key, source_name, source_state, full_rescan)
        self._sync_files(source_key, source_name, source_state, full_rescan)
        if full_rescan:
            source_state["last_rescan_at"] = now
            self._save_state()
        self._heartbeat(source_key, source_name, "HEALTHY", self._latest_timestamp(messages), 0, None)
        logging.info("群同步完成，群=%s，新增候选=%d", source_name, len(pending))

    def _refresh_rebuild_state(self, source_state):
        status = self._request_json("GET", "/internal/wechat/rebuild-status", None) or {}
        remote_generation = int(status.get("rebuildGeneration", 0))
        local_generation = int(self.state.get("rebuild_generation", 0))
        if status.get("rebuildState"):
            raise RebuildInProgress("服务端正在重构微信数据")
        # 恢复旧备份后服务端代次可能低于本地代次，任何不一致都必须触发全量重传。
        if remote_generation != local_generation:
            for key in ("message_timestamp", "message_id", "image_timestamp", "image_id", "file_timestamp", "file_id", "last_rescan_at", "last_reconcile_at"):
                source_state.pop(key, None)
            source_state.pop("image_retry_items", None)
            source_state.pop("file_retry_items", None)
            source_state.pop("reconcile_missing_message_ids", None)
            self.state["rebuild_generation"] = remote_generation
            self.state.pop("_sync_run_id", None)
            self._save_state()

    def _reconcile_recent(self, source_key, source_name, source_state, messages, now):
        if now - int(source_state.get("last_reconcile_at", 0)) < RECONCILE_INTERVAL_SECONDS:
            return
        window_end = now
        window_start = now - 7 * 24 * 60 * 60
        recent = [item for item in messages if window_start <= self._timestamp(item) < window_end and self._message_text(item)]
        requests = [self._message_request(source_key, item) for item in recent]
        canonical = "\n".join(
            f"{item['localMessageId']}\0{item['contentFingerprint']}"
            for item in sorted(requests, key=lambda value: (value["sentAt"], value["localMessageId"]))
        )
        response = self._request_json("POST", "/internal/wechat/sync/reconcile", {
            "sourceKey": source_key,
            "from": iso_time(window_start),
            "to": iso_time(window_end),
            "localCount": len(requests),
            "localDigest": hashlib.sha256(canonical.encode()).hexdigest(),
            "localMessageIds": [item["localMessageId"] for item in requests],
        }) or {}
        if not response.get("countMatch", True) or not response.get("digestMatch", True):
            source_state["reconcile_missing_message_ids"] = response.get("missingLocalMessageIds") or []
            self._heartbeat(source_key, source_name, "CATCHING_UP", self._latest_timestamp(messages), len(source_state["reconcile_missing_message_ids"]), "RECONCILE_MISMATCH")
        source_state["last_reconcile_at"] = now
        self._save_state()

    def _sync_images(self, source_key, source_name, source_state, full_rescan):
        since = self._since_date(source_state.get("image_timestamp"), full_rescan)
        payload = self._wx_json(["attachments", source_key, "--kind", "image", "--json", "--since", since, "-n", str(MAX_HISTORY)])
        status = (payload.get("meta") or {}).get("status", "ok")
        if status in {"possibly_stale_unknown_shards", "possibly_stale"}:
            raise RuntimeError("微信图片数据可能不完整")
        attachments = sorted(payload.get("attachments") or payload.get("messages") or [], key=self._message_position)
        cursor = (int(source_state.get("image_timestamp", 0)), str(source_state.get("image_id", "")))
        now = int(time.time())
        retry_items = {
            str(item.get("attachment_id") or ""): item
            for item in source_state.get("image_retry_items") or []
            if item.get("attachment_id")
        }
        legacy_retry_ids = set(source_state.get("image_retry_ids") or [])
        discovered = [
            item for item in attachments
            if full_rescan
            or self._message_position(item) > cursor
            or str(item.get("local_id") or item.get("message_id") or "") in legacy_retry_ids
        ]
        due_retries = [item for item in retry_items.values() if int(item.get("next_retry_at", 0)) <= now]
        pending_by_id = {
            str(item.get("attachment_id") or ""): item
            for item in (*due_retries, *discovered)
            if item.get("attachment_id")
        }
        pending = sorted(pending_by_id.values(), key=self._message_position)
        for attachment in pending:
            uploaded = self._upload_image(source_key, source_name, attachment)
            timestamp, local_id = max(cursor, self._message_position(attachment))
            retry_key = str(attachment.get("attachment_id") or "")
            if uploaded:
                retry_items.pop(retry_key, None)
            elif retry_key:
                attempts = int(retry_items.get(retry_key, {}).get("attempts", 0)) + 1
                retry_items[retry_key] = self._image_retry_record(attachment, attempts, now)
            source_state["image_timestamp"] = timestamp
            source_state["image_id"] = local_id
            cursor = timestamp, local_id
            source_state["image_retry_items"] = sorted(
                retry_items.values(),
                key=lambda item: (int(item.get("next_retry_at", 0)), str(item.get("attachment_id") or "")),
            )[-MAX_IMAGE_RETRY_ITEMS:]
            source_state.pop("image_retry_ids", None)
            self._save_state()

    def _image_retry_record(self, attachment, attempts, now):
        delay = min(IMAGE_RETRY_MAX_SECONDS, IMAGE_RETRY_BASE_SECONDS * (2 ** min(attempts - 1, 6)))
        return {
            "attachment_id": str(attachment.get("attachment_id") or ""),
            "local_id": str(attachment.get("local_id") or attachment.get("message_id") or ""),
            "timestamp": self._timestamp(attachment),
            "attempts": attempts,
            "next_retry_at": now + delay,
        }

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
            "sourceQuality": "UNKNOWN",
        }
        with tempfile.TemporaryDirectory(prefix="plateview-wechat-") as temporary_directory:
            original = pathlib.Path(temporary_directory) / "original"
            try:
                completed = self._run([self.wx_command, "extract", attachment_id, "-o", str(original), "--overwrite", "--json"])
                report = json.loads(completed.stdout) if completed.stdout.strip().startswith("{") else {}
                if not original.is_file():
                    reported_output = pathlib.Path(str(report.get("output") or ""))
                    if reported_output.is_file() and reported_output.parent == pathlib.Path(temporary_directory):
                        original = reported_output
                if not original.is_file():
                    extracted = [path for path in pathlib.Path(temporary_directory).iterdir() if path.is_file()]
                    if len(extracted) != 1:
                        raise FileNotFoundError("微信图片解密后未找到唯一输出文件")
                    original = extracted[0]
                original = self._prepare_extracted_image(original, report, pathlib.Path(temporary_directory))
                source_quality = str(report.get("resource_quality") or "ORIGINAL").upper()
                if source_quality not in {"ORIGINAL", "HIGH_DEFINITION", "THUMBNAIL"}:
                    source_quality = "UNKNOWN"
                fields["sourceQuality"] = source_quality
                fields["sha256"] = file_sha256(original)
                derivatives = self._create_derivatives(original, pathlib.Path(temporary_directory))
                if source_quality == "THUMBNAIL":
                    files = {"thumbnail": derivatives["thumbnail"]} if derivatives.get("thumbnail") else {}
                else:
                    fields["originalContentType"] = mimetypes.guess_type(original.name)[0] or detect_image_type(original)
                    files = {"original": original}
                    files.update(derivatives)
                self._request_multipart("/internal/wechat/images", fields, files)
                return source_quality in {"ORIGINAL", "HIGH_DEFINITION"}
            except (OSError, ValueError, subprocess.CalledProcessError):
                self._request_multipart("/internal/wechat/images", fields, {})
                return False

    def _prepare_extracted_image(self, original, report, directory):
        if str(report.get("format") or "").lower() != "hevc":
            return original
        converted = directory / "original.jpg"
        self._run([
            "ffmpeg", "-v", "error", "-f", "hevc", "-i", str(original),
            "-frames:v", "1", "-q:v", "2", "-y", str(converted),
        ])
        if not converted.is_file() or converted.stat().st_size == 0:
            raise OSError("微信HEVC图片转换失败")
        return converted

    def _sync_files(self, source_key, source_name, source_state, full_rescan):
        since = self._since_date(source_state.get("file_timestamp"), full_rescan)
        payload = self._wx_json(["history", source_key, "--type", "file", "--json", "--since", since, "-n", str(MAX_HISTORY)])
        status = (payload.get("meta") or {}).get("status", "ok")
        if status in {"possibly_stale_unknown_shards", "possibly_stale"}:
            raise RuntimeError("微信文件数据可能不完整")
        messages = sorted(payload.get("messages") or [], key=self._message_position)
        cursor = (int(source_state.get("file_timestamp", 0)), str(source_state.get("file_id", "")))
        retry_items = {str(item.get("attachment_id")): item for item in source_state.get("file_retry_items", []) if item.get("attachment_id")}
        pending = [
            item for item in messages
            if self._pdf_file_name(item) and (full_rescan or self._message_position(item) > cursor)
        ]
        pending_by_id = {f"pdf-{item.get('local_id') or item.get('id')}": item for item in pending}
        now = int(time.time())
        for retry in retry_items.values():
            if int(retry.get("next_retry_at", 0)) <= now:
                message = next((item for item in messages if f"pdf-{item.get('local_id') or item.get('id')}" == retry["attachment_id"]), None)
                if message is not None:
                    pending_by_id[retry["attachment_id"]] = message
        for attachment_id, message in sorted(pending_by_id.items(), key=lambda item: self._message_position(item[1])):
            try:
                self._upload_pdf(source_key, source_name, message)
                retry_items.pop(attachment_id, None)
            except (OSError, ValueError, subprocess.CalledProcessError):
                attempts = int(retry_items.get(attachment_id, {}).get("attempts", 0)) + 1
                retry_items[attachment_id] = {
                    "attachment_id": attachment_id,
                    "local_id": str(message.get("local_id") or message.get("id") or ""),
                    "timestamp": self._timestamp(message),
                    "attempts": attempts,
                    "next_retry_at": now + min(IMAGE_RETRY_MAX_SECONDS, IMAGE_RETRY_BASE_SECONDS * (2 ** min(attempts - 1, 6))),
                }
            timestamp, local_id = max(cursor, self._message_position(message))
            source_state["file_timestamp"] = timestamp
            source_state["file_id"] = local_id
            cursor = timestamp, local_id
            source_state["file_retry_items"] = sorted(retry_items.values(), key=lambda item: (int(item.get("next_retry_at", 0)), item["attachment_id"]))[-MAX_ATTACHMENT_RETRY_ITEMS:]
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
            "localMessageId": local_id,
            "senderUsername": str(message.get("sender_username") or ""),
            "senderDisplay": str(message.get("sender") or message.get("sender_contact_display") or ""),
            "sentAt": iso_time(self._timestamp(message)),
            "attachmentKind": "PDF",
            "fileName": file_name,
            "originalContentType": "application/pdf",
            "sourceQuality": "UNKNOWN",
        }
        original = self._locate_pdf(message)
        if original is None:
            self._request_multipart("/internal/wechat/attachments", fields, {})
            return
        fields["sourceQuality"] = "ORIGINAL"
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

    def _batch_digest(self, messages):
        canonical = "\n".join(
            f"{item['localMessageId']}\0{int(dt.datetime.fromisoformat(item['sentAt'].replace('Z', '+00:00')).timestamp())}\0{item['contentFingerprint']}"
            for item in sorted(messages, key=lambda value: (value["sentAt"], value["localMessageId"]))
        )
        return hashlib.sha256(canonical.encode()).hexdigest()

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
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
        request = urllib.request.Request(
            self.server_url + path,
            data=data,
            method=method,
            headers={"Content-Type": "application/json", "X-PlateView-Collector-Token": self.token},
        )
        with self._urlopen_with_retry(request, timeout=30) as response:
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
        with self._urlopen_with_retry(request, timeout=120) as response:
            response.read()

    def _urlopen_with_retry(self, request, timeout):
        for attempt, delay in enumerate((*REQUEST_RETRY_DELAYS, None), start=1):
            try:
                return urllib.request.urlopen(request, timeout=timeout)
            except urllib.error.HTTPError as error:
                if error.code in {409, 423}:
                    try:
                        payload = json.loads(error.read().decode("utf-8"))
                    except (OSError, ValueError):
                        payload = {}
                    if payload.get("code") == "WECHAT_REBUILD_IN_PROGRESS":
                        raise RebuildInProgress("服务端正在重构微信数据")
                if error.code < 500 or delay is None:
                    raise
                logging.warning("服务器暂时不可用，状态码=%d，第%d次请求稍后重试", error.code, attempt)
            except (urllib.error.URLError, TimeoutError) as error:
                if delay is None:
                    raise
                reason = getattr(error, "reason", error)
                logging.warning("网络请求暂时失败，原因类型=%s，第%d次请求稍后重试", type(reason).__name__, attempt)
            time.sleep(delay)

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

# XifanSign

喜番签名 LSPosed 模块 — 在喜番进程内起 HTTP 签名服务，不走 Frida，不会被反调试检测。

## 接口

| 方法 | 路径 | 参数 | 返回 |
|---|---|---|---|
| GET | `/health` | - | `{"ready": true, "kaw": "..."}` |
| POST | `/sign` | `{"url": "/rest/e/tube/...", "body": "加密body"}` | `{"headers": {"Ks-Sig3": "...", "Kaw": "...", "kas": "..."}}` |
| POST | `/encrypt` | `{"plaintext": "json字符串"}` | `{"message": "加密结果"}` |
| POST | `/decrypt` | `{"message": "加密数据"}` | `{"data": "解密结果"}` |

默认端口 `5599`，只监听 `127.0.0.1`。

## 安装

1. 从 GitHub Actions 下载 APK
2. LSPosed 中启用模块
3. 作用域勾选 `com.kwai.theater`（喜番）
4. 重启喜番
5. `adb forward tcp:5599 tcp:5599`

## 配合 xifan.py 使用

```bash
python xifan.py --sign-url http://127.0.0.1:5599 --ck-file ck.txt
```

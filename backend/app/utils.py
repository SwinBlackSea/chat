from urllib.parse import urlsplit


def mask_key(key: str) -> str:
    if len(key) <= 8:
        return "****"
    return f"{key[:4]}****{key[-4:]}"


def normalize_base_url(value: str) -> str:
    url = value.strip().rstrip("/")
    parsed = urlsplit(url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ValueError("baseUrl 必须是有效的 http(s) 地址")
    if parsed.username or parsed.password:
        raise ValueError("baseUrl 不能包含用户名或密码")
    return url

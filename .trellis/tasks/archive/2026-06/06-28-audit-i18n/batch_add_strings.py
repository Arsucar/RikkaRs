#!/usr/bin/env python3
"""One-off: add i18n audit keys to app strings.xml (en + 5 locales)."""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]

from lxml import etree


def update_entry(file_path: Path, key: str, value: str) -> None:
    if not file_path.exists():
        raise FileNotFoundError(file_path)
    with open(file_path, "r", encoding="utf-8") as f:
        tree = etree.parse(f)
    root = tree.getroot()
    found = False
    for string_elem in root.findall("string"):
        if string_elem.get("name") == key:
            string_elem.text = value
            found = True
            break
    if not found:
        string_elem = etree.SubElement(root, "string")
        string_elem.set("name", key)
        string_elem.text = value
    etree.indent(root, space="  ")
    tree.write(str(file_path), encoding="utf-8", xml_declaration=True, pretty_print=True)

RES = ROOT / "app" / "src" / "main" / "res"
LOCALES = ["values", "values-zh", "values-zh-rTW", "values-ja", "values-ko-rKR", "values-ru"]

# key -> per-locale value (values = English)
ENTRIES: dict[str, dict[str, str]] = {
    "donate_page_afdian_title": {
        "values": "Afdian",
        "values-zh": "爱发电",
        "values-zh-rTW": "愛發電",
        "values-ja": "Afdian",
        "values-ko-rKR": "Afdian",
        "values-ru": "Afdian",
    },
    "setting_asr_configure_step_hotwords_placeholder": {
        "values": "hotword1, hotword2, hotword3",
        "values-zh": "热词1, 热词2, 热词3",
        "values-zh-rTW": "熱詞1, 熱詞2, 熱詞3",
        "values-ja": "hotword1, hotword2, hotword3",
        "values-ko-rKR": "hotword1, hotword2, hotword3",
        "values-ru": "hotword1, hotword2, hotword3",
    },
    "setting_tts_configure_step_api_key_desc": {
        "values": "Get your API key from StepFun: platform.stepfun.com/interface-key",
        "values-zh": "从阶跃星辰官网获取密钥: platform.stepfun.com/interface-key",
        "values-zh-rTW": "從階躍星辰官網取得金鑰: platform.stepfun.com/interface-key",
        "values-ja": "StepFun から API キーを取得: platform.stepfun.com/interface-key",
        "values-ko-rKR": "StepFun에서 API 키 발급: platform.stepfun.com/interface-key",
        "values-ru": "Получите ключ API на StepFun: platform.stepfun.com/interface-key",
    },
    "setting_tts_configure_step_api_key_placeholder": {
        "values": "API key from StepFun",
        "values-zh": "从阶跃星辰官网获取密钥",
        "values-zh-rTW": "從階躍星辰官網取得金鑰",
        "values-ja": "StepFun の API キー",
        "values-ko-rKR": "StepFun API 키",
        "values-ru": "Ключ API StepFun",
    },
    "setting_tts_configure_step_format_desc": {
        "values": "Audio encoding format (StepFun API uses camelCase field names)",
        "values-zh": "音频编码格式 (注意 StepFun API 字段名为 camelCase)",
        "values-zh-rTW": "音訊編碼格式 (StepFun API 欄位為 camelCase)",
        "values-ja": "音声エンコード形式（StepFun API は camelCase）",
        "values-ko-rKR": "오디오 인코딩 형식 (StepFun API는 camelCase)",
        "values-ru": "Формат кодирования аудио (поля StepFun API в camelCase)",
    },
    "setting_tts_configure_step_speed_desc": {
        "values": "Speed (0.5–2.0, 1.0 is normal)",
        "values-zh": "语速 (0.5 - 2.0, 1.0 为正常)",
        "values-zh-rTW": "語速 (0.5 - 2.0, 1.0 為正常)",
        "values-ja": "速度 (0.5–2.0、1.0 が標準)",
        "values-ko-rKR": "속도 (0.5–2.0, 1.0이 기본)",
        "values-ru": "Скорость (0.5–2.0, 1.0 — норма)",
    },
    "setting_tts_configure_step_volume_desc": {
        "values": "Volume (0.1–2.0, 1.0 is normal)",
        "values-zh": "音量 (0.1 - 2.0, 1.0 为正常)",
        "values-zh-rTW": "音量 (0.1 - 2.0, 1.0 為正常)",
        "values-ja": "音量 (0.1–2.0、1.0 が標準)",
        "values-ko-rKR": "음량 (0.1–2.0, 1.0이 기본)",
        "values-ru": "Громкость (0.1–2.0, 1.0 — норма)",
    },
    "setting_tts_configure_step_sample_rate_desc": {
        "values": "Sample rate (Hz)",
        "values-zh": "采样率 (Hz)",
        "values-zh-rTW": "取樣率 (Hz)",
        "values-ja": "サンプルレート (Hz)",
        "values-ko-rKR": "샘플 레이트 (Hz)",
        "values-ru": "Частота дискретизации (Гц)",
    },
    "setting_tts_configure_step_instruction_desc": {
        "values": "Global instruction; only stepaudio-2.5-tts (≤200 chars, omit if empty)",
        "values-zh": "全局语境指令, 仅 stepaudio-2.5-tts 生效 (≤200 字符, 留空不下发)",
        "values-zh-rTW": "全域語境指令，僅 stepaudio-2.5-tts 生效 (≤200 字元，留空則不下發)",
        "values-ja": "グローバル指示（stepaudio-2.5-tts のみ、200 文字以内、空なら送信しない）",
        "values-ko-rKR": "전역 지시문; stepaudio-2.5-tts만 (200자 이하, 비우면 미전송)",
        "values-ru": "Глобальная инструкция; только stepaudio-2.5-tts (≤200 симв., пусто — не отправлять)",
    },
    "setting_tts_configure_step_instruction_placeholder": {
        "values": "e.g. gentle tone, slower pace",
        "values-zh": "例如: 语气温柔, 语速偏慢",
        "values-zh-rTW": "例如: 語氣溫柔, 語速偏慢",
        "values-ja": "例: 穏やかな口調、ゆっくり",
        "values-ko-rKR": "예: 부드러운 어조, 느린 속도",
        "values-ru": "напр.: мягкий тон, медленнее",
    },
}

IMGGEN = {
    "imggen_page_search_keyword_placeholder": (
        "Search images by keyword",
        "搜索图片关键字",
        "搜尋圖片關鍵字",
        "画像をキーワードで検索",
        "이미지 키워드 검색",
        "Поиск изображений по ключевым словам",
    ),
    "imggen_page_reference_added": (
        "Added as reference image",
        "已添加为引用图",
        "已新增為引用圖",
        "参照画像として追加しました",
        "참조 이미지로 추가됨",
        "Добавлено как опорное изображение",
    ),
    "imggen_page_reference_failed": (
        "Failed to add reference: %1$s",
        "引用图片失败：%1$s",
        "引用圖片失敗：%1$s",
        "参照画像の追加に失敗: %1$s",
        "참조 이미지 추가 실패: %1$s",
        "Не удалось добавить ссылку: %1$s",
    ),
    "imggen_page_manage_quick_messages": (
        "Manage image quick messages",
        "管理图像快捷消息",
        "管理圖像快捷訊息",
        "画像クイックメッセージを管理",
        "이미지 빠른 메시지 관리",
        "Управление быстрыми сообщениями для изображений",
    ),
    "imggen_page_display_mode": (
        "Display mode",
        "显示模式",
        "顯示模式",
        "表示モード",
        "표시 모드",
        "Режим отображения",
    ),
    "imggen_page_display_mode_grid": ("Grid", "网格", "網格", "グリッド", "그리드", "Сетка"),
    "imggen_page_display_mode_grouped": ("Grouped", "分组", "分組", "グループ", "그룹", "Группы"),
    "imggen_page_trash": ("Trash", "回收站", "回收站", "ゴミ箱", "휴지통", "Корзина"),
    "imggen_page_space": ("Space", "空间", "空間", "スペース", "공간", "Пространство"),
    "imggen_page_job_edit": ("Edit", "编辑", "編輯", "編集", "편집", "Редактировать"),
    "imggen_page_job_generate": ("Generate", "生成", "生成", "生成", "생성", "Сгенерировать"),
    "imggen_page_job_queued": ("Queued…", "排队中…", "排隊中…", "待機中…", "대기 중…", "В очереди…"),
    "imggen_page_job_generating": ("Generating…", "生成中…", "生成中…", "生成中…", "생성 중…", "Генерация…"),
    "imggen_page_untitled": ("Untitled", "未命名", "未命名", "無題", "제목 없음", "Без названия"),
    "imggen_page_image_quick_messages": (
        "Image quick messages",
        "图像快捷消息",
        "圖像快捷訊息",
        "画像クイックメッセージ",
        "이미지 빠른 메시지",
        "Быстрые сообщения для изображений",
    ),
    "imggen_page_image_quick_messages_empty": (
        "No image quick messages yet",
        "暂无图像快捷消息",
        "暫無圖像快捷訊息",
        "画像クイックメッセージはまだありません",
        "이미지 빠른 메시지가 없습니다",
        "Быстрых сообщений для изображений пока нет",
    ),
    "imggen_page_add": ("Add", "添加", "新增", "追加", "추가", "Добавить"),
    "imggen_page_add_image_quick_message": (
        "Add image quick message",
        "添加图像快捷消息",
        "新增圖像快捷訊息",
        "画像クイックメッセージを追加",
        "이미지 빠른 메시지 추가",
        "Добавить быстрое сообщение для изображения",
    ),
    "imggen_page_edit_image_quick_message": (
        "Edit image quick message",
        "编辑图像快捷消息",
        "編輯圖像快捷訊息",
        "画像クイックメッセージを編集",
        "이미지 빠른 메시지 편집",
        "Редактировать быстрое сообщение",
    ),
    "imggen_page_title_label": ("Title", "标题", "標題", "タイトル", "제목", "Заголовок"),
    "imggen_page_content_label": ("Content", "内容", "內容", "内容", "내용", "Содержание"),
    "imggen_page_ungrouped": ("Ungrouped", "未分组", "未分組", "未分類", "미분류", "Без группы"),
    "imggen_page_deleted_collection": (
        "Deleted collection",
        "已删除分组",
        "已刪除分組",
        "削除されたコレクション",
        "삭제된 컬렉션",
        "Удалённая коллекция",
    ),
    "imggen_page_favorites_count": (
        "%1$d favorites",
        "收藏 %1$d 张",
        "收藏 %1$d 張",
        "お気に入り %1$d 枚",
        "즐겨찾기 %1$d장",
        "Избранное: %1$d",
    ),
    "imggen_page_collapse_all": ("Collapse all", "全部折叠", "全部摺疊", "すべて折りたたむ", "모두 접기", "Свернуть все"),
    "imggen_page_expand_all": ("Expand all", "全部展开", "全部展開", "すべて展開", "모두 펼치기", "Развернуть все"),
    "imggen_page_new_collection": ("New collection", "新建分组", "新建分組", "新規コレクション", "새 컬렉션", "Новая коллекция"),
    "imggen_page_images_count": ("%1$d images", "%1$d 张", "%1$d 張", "%1$d 枚", "%1$d장", "%1$d изображений"),
    "imggen_page_collection_name": (
        "Collection name",
        "分组名称",
        "分組名稱",
        "コレクション名",
        "컬렉션 이름",
        "Название коллекции",
    ),
    "imggen_page_rename_collection": (
        "Rename collection",
        "重命名分组",
        "重新命名分組",
        "コレクション名を変更",
        "컬렉션 이름 변경",
        "Переименовать коллекцию",
    ),
    "imggen_page_delete_collection": ("Delete collection", "删除分组", "刪除分組", "コレクションを削除", "컬렉션 삭제", "Удалить коллекцию"),
    "imggen_page_collection_delete_confirm": (
        'Deleting collection "%1$s" moves its favorites to Ungrouped; favorites are not removed.',
        "将删除分组「%1$s」，其中收藏会移入未分组，不会取消收藏。",
        "將刪除分組「%1$s」，其中收藏會移入未分組，不會取消收藏。",
        "コレクション「%1$s」を削除すると、お気に入りは未分類に移動します（お気に入りは解除されません）。",
        '컬렉션 "%1$s"을(를) 삭제하면 즐겨찾기는 미분류로 이동하며 즐겨찾기는 유지됩니다.',
        "Удаление коллекции «%1$s» перенесёт избранное в «Без группы»; избранное не снимается.",
    ),
    "imggen_page_rename": ("Rename", "重命名", "重新命名", "名前を変更", "이름 변경", "Переименовать"),
    "imggen_page_delete_collection_action": (
        "Delete collection",
        "删除分组",
        "刪除分組",
        "コレクションを削除",
        "컬렉션 삭제",
        "Удалить коллекцию",
    ),
    "imggen_page_collection": ("Collection", "分组", "分組", "コレクション", "컬렉션", "Коллекция"),
    "imggen_page_no_favorites": (
        "No favorites yet",
        "暂无收藏",
        "暫無收藏",
        "お気に入りはまだありません",
        "즐겨찾기가 없습니다",
        "Избранного пока нет",
    ),
    "imggen_page_group_meta_variants": (
        "%1$s · %2$d images · %3$d variants",
        "%1$s · %2$d 张 · %3$d 变体",
        "%1$s · %2$d 張 · %3$d 變體",
        "%1$s · %2$d 枚 · %3$d バリエーション",
        "%1$s · %2$d장 · %3$d 변형",
        "%1$s · %2$d изобр. · %3$d вариантов",
    ),
    "imggen_page_group_meta": (
        "%1$s · %2$d images",
        "%1$s · %2$d 张",
        "%1$s · %2$d 張",
        "%1$s · %2$d 枚",
        "%1$s · %2$d장",
        "%1$s · %2$d изобр.",
    ),
    "imggen_page_section_meta": (
        "%1$s · %2$s · %3$d images",
        "%1$s · %2$s · %3$d 张",
        "%1$s · %2$s · %3$d 張",
        "%1$s · %2$s · %3$d 枚",
        "%1$s · %2$s · %3$d장",
        "%1$s · %2$s · %3$d изобр.",
    ),
    "imggen_page_favorite_added": ("Favorited", "已收藏", "已收藏", "お気に入りに追加", "즐겨찾기에 추가됨", "В избранном"),
    "imggen_page_favorite_removed": ("Unfavorited", "已取消收藏", "已取消收藏", "お気に入りを解除", "즐겨찾기 해제", "Убрано из избранного"),
    "imggen_page_favorite_failed": (
        "Favorite failed: %1$s",
        "收藏失败：%1$s",
        "收藏失敗：%1$s",
        "お気に入りに失敗: %1$s",
        "즐겨찾기 실패: %1$s",
        "Ошибка избранного: %1$s",
    ),
    "imggen_page_unknown_error": ("Unknown error", "未知错误", "未知錯誤", "不明なエラー", "알 수 없는 오류", "Неизвестная ошибка"),
    "imggen_page_no_prompt": ("No prompt", "无提示词", "無提示詞", "プロンプトなし", "프롬프트 없음", "Нет промпта"),
    "imggen_page_prompt_title": ("Prompt", "提示词", "提示詞", "プロンプト", "프롬프트", "Промпт"),
    "imggen_page_close": ("Close", "关闭", "關閉", "閉じる", "닫기", "Закрыть"),
    "imggen_page_trash_empty": ("Trash is empty", "回收站为空", "回收站為空", "ゴミ箱は空です", "휴지통이 비어 있습니다", "Корзина пуста"),
    "imggen_page_trash_clear": ("Clear", "清空", "清空", "空にする", "비우기", "Очистить"),
    "imggen_page_trash_clear_title": ("Clear trash?", "清空回收站？", "清空回收站？", "ゴミ箱を空にしますか？", "휴지통을 비울까요?", "Очистить корзину?"),
    "imggen_page_trash_clear_message": (
        "Permanently delete all images and records in trash. This cannot be undone.",
        "将彻底删除回收站内的所有图片和记录，无法恢复。",
        "將徹底刪除回收站內的所有圖片與記錄，無法復原。",
        "ゴミ箱内の画像と記録を完全に削除します。元に戻せません。",
        "휴지통의 모든 이미지와 기록을 영구 삭제합니다. 되돌릴 수 없습니다.",
        "Безвозвратно удалить все изображения и записи в корзине.",
    ),
    "imggen_page_restore": ("Restore", "恢复", "恢復", "復元", "복원", "Восстановить"),
    "imggen_page_delete_permanently_title": (
        "Delete image permanently?",
        "彻底删除图片？",
        "徹底刪除圖片？",
        "画像を完全に削除しますか？",
        "이미지를 영구 삭제할까요?",
        "Удалить изображение навсегда?",
    ),
    "imggen_page_delete_permanently_message": (
        "This deletes local files and records. This cannot be undone.",
        "此操作会删除本地文件和记录，无法恢复。",
        "此操作會刪除本機檔案與記錄，無法復原。",
        "ローカルファイルと記録を削除します。元に戻せません。",
        "로컬 파일과 기록을 삭제합니다. 되돌릴 수 없습니다.",
        "Удаляются локальные файлы и записи. Отменить нельзя.",
    ),
    "imggen_page_delete_permanently": ("Delete permanently", "彻底删除", "徹底刪除", "完全に削除", "영구 삭제", "Удалить навсегда"),
    "imggen_page_unit_images": ("images", "张", "張", "枚", "장", "шт."),
    "imggen_page_unit_items": ("items", "个", "個", "件", "개", "шт."),
    "imggen_page_concurrent_requests": (
        "Concurrent requests",
        "并发请求数",
        "並發請求數",
        "同時リクエスト数",
        "동시 요청 수",
        "Параллельные запросы",
    ),
    "imggen_page_concurrent_requests_desc": (
        "Max in-flight API requests; completed jobs do not count toward the limit",
        "同时进行中的 API 请求上限；成功或失败的任务卡片不占名额，可继续发送",
        "同時進行中的 API 請求上限；成功或失敗的任務卡片不佔名額，可繼續傳送",
        "同時実行中の API リクエスト上限。完了したジョブは枠に含まれません",
        "진행 중 API 요청 상한. 완료된 작업은 한도에 포함되지 않습니다",
        "Лимит одновременных API-запросов; завершённые задачи не занимают слот",
    ),
    "imggen_page_streaming_preview": ("Streaming preview", "流式预览", "串流預覽", "ストリーミングプレビュー", "스트리밍 미리보기", "Потоковый предпросмотр"),
    "imggen_page_streaming_preview_desc": (
        "Show partial images while generating; turn off if your API does not support streaming (SSE)",
        "开启后边生成边显示部分图；若你的接口/中转不支持流式(SSE)，请关闭",
        "開啟後邊生成邊顯示部分圖；若介面/中轉不支援串流 (SSE)，請關閉",
        "生成中に部分画像を表示。ストリーミング (SSE) 非対応ならオフにしてください",
        "생성 중 부분 이미지 표시. 스트리밍(SSE) 미지원 시 끄세요",
        "Показывать частичные изображения при генерации; выключите, если API не поддерживает SSE",
    ),
    "imggen_page_gpt_image2_size": ("gpt-image-2 size", "gpt-image-2 尺寸", "gpt-image-2 尺寸", "gpt-image-2 サイズ", "gpt-image-2 크기", "Размер gpt-image-2"),
    "imggen_page_gpt_image2_size_desc": (
        "auto, presets, or custom WxH; custom sizes are validated before generation",
        "支持 auto、常用尺寸和自定义宽x高；自定义会在生成前校验",
        "支援 auto、常用尺寸與自訂寬x高；自訂會在生成前驗證",
        "auto、プリセット、カスタム幅×高さ。カスタムは生成前に検証",
        "auto, 프리셋, 사용자 지정 WxH. 사용자 지정은 생성 전 검증",
        "auto, пресеты или WxH; пользовательский размер проверяется до генерации",
    ),
    "imggen_page_quality": ("Quality", "质量", "品質", "品質", "품질", "Качество"),
    "imggen_page_quality_desc": (
        "Sends quality only when model ID is gpt-image-2",
        "仅在模型 ID 为 gpt-image-2 时发送 quality 字段",
        "僅在模型 ID 為 gpt-image-2 時傳送 quality 欄位",
        "モデル ID が gpt-image-2 のときのみ quality を送信",
        "모델 ID가 gpt-image-2일 때만 quality 전송",
        "Поле quality отправляется только для gpt-image-2",
    ),
    "imggen_page_output_format": ("Output format", "输出格式", "輸出格式", "出力形式", "출력 형식", "Формат вывода"),
    "imggen_page_output_format_desc": (
        "output_format; default png; jpeg/webp support compression",
        "对应 output_format；默认 png，jpeg/webp 可配置压缩",
        "對應 output_format；預設 png，jpeg/webp 可設定壓縮",
        "output_format。既定 png、jpeg/webp は圧縮可",
        "output_format; 기본 png, jpeg/webp 압축 설정 가능",
        "output_format; по умолчанию png; jpeg/webp — сжатие",
    ),
    "imggen_page_output_compression": ("Output compression", "输出压缩", "輸出壓縮", "出力圧縮", "출력 압축", "Сжатие вывода"),
    "imggen_page_output_compression_desc": (
        "output_compression for jpeg/webp only, range 0–100",
        "仅 jpeg/webp 发送 output_compression，范围 0-100",
        "僅 jpeg/webp 傳送 output_compression，範圍 0-100",
        "jpeg/webp のみ output_compression、0–100",
        "jpeg/webp만 output_compression, 0–100",
        "output_compression только для jpeg/webp, 0–100",
    ),
    "imggen_page_background": ("Background", "背景", "背景", "背景", "배경", "Фон"),
    "imggen_page_background_desc": (
        "gpt-image-2 does not support transparency; only auto/opaque",
        "gpt-image-2 不适配透明背景，仅保留 auto/opaque",
        "gpt-image-2 不支援透明背景，僅保留 auto/opaque",
        "gpt-image-2 は透過非対応。auto/opaque のみ",
        "gpt-image-2는 투명 미지원. auto/opaque만",
        "gpt-image-2 без прозрачности; только auto/opaque",
    ),
    "imggen_page_moderation": ("Moderation", "审核", "審核", "モデレーション", "검수", "Модерация"),
    "imggen_page_moderation_desc": (
        "moderation; auto is default filtering, low is more permissive",
        "对应 moderation；auto 为默认过滤，low 较宽松",
        "對應 moderation；auto 為預設過濾，low 較寬鬆",
        "moderation。auto は既定、low は緩め",
        "moderation; auto 기본 필터, low는 완화",
        "moderation: auto — стандарт, low — мягче",
    ),
    "imggen_page_columns": ("Columns", "列数", "欄數", "列数", "열 수", "Столбцы"),
    "imggen_page_decrease_columns": ("Decrease columns", "减少列数", "減少欄數", "列数を減らす", "열 수 줄이기", "Уменьшить число столбцов"),
    "imggen_page_increase_columns": ("Increase columns", "增加列数", "增加欄數", "列数を増やす", "열 수 늘리기", "Увеличить число столбцов"),
    "imggen_page_search_images": ("Search images", "搜索图片", "搜尋圖片", "画像を検索", "이미지 검색", "Поиск изображений"),
}

for key, tup in IMGGEN.items():
    ENTRIES[key] = dict(zip(LOCALES, tup, strict=True))


def main() -> None:
    for key, loc_vals in ENTRIES.items():
        for loc in LOCALES:
            path = RES / loc / "strings.xml"
            if not path.exists():
                print(f"skip missing {path}")
                continue
            val = loc_vals[loc]
            update_entry(path, key, val)
            print(f"ok {loc} {key}")


if __name__ == "__main__":
    main()
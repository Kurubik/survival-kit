# Lastbook — проверка пригодности для Survival Kit

Дата: 17 ноября 2026. Метод: чтение README, лицензий и исходников; приложение и опубликованные сборки не запускались. Пакеты библиотеки и AI-модели не скачивались. Производительность и корректность ответов на Android не проверены.

Репозиторий: https://github.com/Immortal-Protocols/lastbook
Зафиксированный commit: `7b5fb1442a3b05475225e6a7ff90074934476470`.

## Подтверждено кодом и структурой

| Факт | Основание | Значение для проекта |
|---|---|---|
| Desktop Python + обычный web UI | [README](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/README.md), `app/`, `engine/`, `web/` | Не готовый Android-клиент |
| Мобильная ветка — native Swift/iPad preview | [mobile/ipad/README.md](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/mobile/ipad/README.md) | Полезный пример переноса, но не Kotlin-код |
| В `content/topics/` 14 JSON-файлов | [content/topics](https://github.com/Immortal-Protocols/lastbook/tree/7b5fb1442a3b05475225e6a7ff90074934476470/content/topics) | Есть контентная архитектура, не вся библиотека |
| Памятки привязывают утверждения к страницам | [content/SCHEMA.md](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/content/SCHEMA.md) | Сохранить происхождение и ограничения |
| FTS5-индекс использует `unicode61` | [engine/library.py:200](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/engine/library.py#L200) | Не весь поиск ASCII-only; нужен аккуратный перенос и переиндексация |
| Семантическая модель — `bge-small-en-v1.5` | [retrieval.py:21](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/retrieval.py#L21) | Англоязычная модель не является RU-решением |
| `readable()` фильтрует по английским словам | [retrieval.py:83](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/retrieval.py#L83) | Русские фрагменты отбрасываются в семантическом индексировании |
| Часть query-эвристик ограничена `a-z` | [assistant.py:137](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/assistant.py#L137) | Перевод UI не решает локализацию поиска |
| Промпт требует источники и признание пробелов | [assistant.py:327](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/assistant.py#L327) | Хороший принцип, но не доказательство отсутствия галлюцинаций |
| Проверка цитат валидирует ключи и страницы | [assistant.py:596](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/assistant.py#L596) | Не доказывает, что фраза действительно следует из источника |
| Модель desktop pinned: Gemma 4 26B A4B, 16 947 541 728 байт | [llm_local.py:25](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/llm_local.py#L25) | Для телефона нужна другая модель и измерения |
| Офлайн-карты и адреса ориентированы на США | [docs/MAPS.md:99](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/docs/MAPS.md#L99) | Для другого региона нужны отдельные данные и процесс упаковки |
| Пробелы и противоречия библиотеки перечислены отдельно | [content/gaps.json](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/content/gaps.json) | Нельзя считать исходный корпус полностью готовым походным курсом |

В проверенных исходниках Python/JS/Swift не обнаружен готовый STT/TTS-пользовательский поток. Android-модуль в дереве отсутствует. Вывод основан на структуре и поиске по ключевым компонентам, не на запуске всего приложения.

## Лицензии

- [LICENSE](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/LICENSE): оригинальный код и документация MIT; сохранение copyright и license notice при заимствовании.
- [THIRD_PARTY_NOTICES.md](https://github.com/Immortal-Protocols/lastbook/blob/7b5fb1442a3b05475225e6a7ff90074934476470/THIRD_PARTY_NOTICES.md): разные условия библиотек и моделей; в desktop-бандлах есть GPL-компоненты, в частности python-libzim/libzim.
- Книги, видео и энциклопедии не включены в Git. Разрешения необходимо проверять по каждому выбранному материалу и его Sources and permissions.
- OSM-данные — отдельная атрибуция/ODbL. Лицензия данных не означает разрешение массово скачивать тайлы с любого публичного сервиса.
- Указания авторов о лицензиях моделей — информация исходного проекта, не самостоятельный аудит model card каждого артефакта.

## Что использовать

Форматы каталога и памяток, устойчивые идентификаторы источников, раскрытие страниц/таймкодов, идеи hybrid retrieval, read-only инструменты, проверку пакетов и атомарное обновление.

## Что адаптировать или заменить

Desktop-обвязку, Python-runtime в мобильном приложении, английские фильтры и embedding-модель, desktop LLM, US-ориентированные карты/адреса, громоздкие пакеты и внешний вид. STT/TTS, GPS, чек-листы и походный сценарий спроектировать как новые функции.

## Проверенные внешние технические источники

- [Android SpeechRecognizer, исходный код AOSP](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/speech/SpeechRecognizer.java): стандартный API может отправлять аудио на сервер; on-device recognizer требует проверки доступности. Простого флага «offline» недостаточно для обещания автономности на любом телефоне.
- [Android TTS Voice, исходный код AOSP](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/speech/tts/Voice.java): у голоса есть `isNetworkConnectionRequired()`; наличие и RU-язык надо проверять на устройстве.
- [Vosk models](https://alphacephei.com/vosk/models): `vosk-model-small-ru-0.22`, около 45 МБ, предназначен в том числе для Android/iOS, Apache 2.0. Качество при ветре не измерено в этом исследовании.
- [Qwen3-1.7B](https://huggingface.co/Qwen/Qwen3-1.7B): небольшой multilingual-кандидат, не окончательный выбор и не доказательство достаточного качества русского survival-ассистента.
- [multilingual-e5-small](https://huggingface.co/intfloat/multilingual-e5-small): multilingual-кандидат для поиска с заявленной поддержкой русского; интеграция на Android и качество на выбранном корпусе ещё не проверены.

Публичные страницы Android Developer в этой среде вернули цикл редиректов; определения API сверены по AOSP. Ни один из кандидатов не установлен в рамках этапа планирования.

## Целевой репозиторий

GitHub API сообщил `Kurubik/survival-kit`: public, size 0, default branch `main`; `git ls-remote ... HEAD` не вернул commit. На момент проверки репозиторий пуст. Проверка read-only; изменения и push отсутствуют.

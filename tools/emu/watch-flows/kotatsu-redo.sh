# Kotatsu Redo(9.8.7): Source › 내장 메모리 → `Sea Song` 작품 상세 → `지금 읽기` → 메뉴(작품 이름·화 이름) → 상세 → 홈.
# 에뮬에는 만든 만화 `Sea Song.cbz`(3쪽)를 `/sdcard/RedoManga` 에 두고 지역 만화 디렉토리로 더해 두었다(docs/READER_APPS.md `Kotatsu Redo`).
# Kotatsu Redo 는 떠 있던 화면을 이어 보여 주므로 Kotatsu Redo 만 강제 종료해 첫 탭에서 시작한다(Readblio 는 강제 종료하지 않는다).
P=io.github.kotatsuredo.kotatsu
a shell am force-stop $P
launch $P 6
tap 750 1380 3
tap 277 292 4
shot 1-shelf
tap 145 440 4
shot 2-detail
tap 878 1396 6
tap 536 728 3
shot 3-menu
key KEYCODE_BACK 3
shot 4-detail
home 4

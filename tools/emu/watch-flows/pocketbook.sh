# PocketBook reader(CremaPebble_A11, /sdcard/Books 의 EPUB 셋, Books 탭 목록 보기·추가한 날 순(PocketBook 기본)): 앱 열기(홈 탭) → Books 탭 →
# 둘째 칸(`은빛 마법사의 각성 (1화 ~ 2화)`, 저자 없음) → 읽는 화면 → 오른쪽 눌러 두 쪽 넘기기 → 가운데 눌러 메뉴(제목·진행률) 열고 닫기 → 뒤로 Books 탭 → 홈.
# CremaPebble_A11 은 물리 키가 없다고 monkey 가 멈춰(251) `launch` 대신 실행 화면을 바로 연다.
a shell am start -n com.obreey.reader/.StartActivity >/dev/null; sleep 8
shot 1-home
tap 322 1400 4
shot 2-list
tap 600 838 8
tap 1000 800 2
tap 1000 800 2
tap 536 750 3
shot 3-menu
key KEYCODE_BACK 2
key KEYCODE_BACK 5
shot 4-list
home 5

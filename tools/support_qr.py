"""설정 › 정보 › 후원하기 의 QR 그림(`app/src/main/res/drawable-nodpi/support_*.png`)을 만든다.

모듈 하나가 1px 인 1비트 PNG(둘레 여백 4모듈 포함)로 두고, 앱(`Support.QrView`)이 정수배로 키워 흐리지 않게 그린다.
토스는 넣지 않는다(토스아이디가 끝나 토스 QR 은 계좌번호를 담는다). 주소를 바꾸면 이 파일을 고쳐 다시 돌린다: /usr/bin/python3 tools/support_qr.py (OpenCV·Pillow 필요)
"""
import os

import cv2
from PIL import Image

LINKS = {
    'kakaopay': 'https://qr.kakaopay.com/FDngUrL7d',
    'kofi': 'https://ko-fi.com/hissinger',
    'bmc': 'https://buymeacoffee.com/hissinger',
}

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res', 'drawable-nodpi')

enc = cv2.QRCodeEncoder.create()
det = cv2.QRCodeDetector()
for name, url in LINKS.items():
    m = enc.encode(url)
    # 읽히는지 키운 그림으로 확인
    big = cv2.copyMakeBorder(cv2.resize(m, None, fx=8, fy=8, interpolation=cv2.INTER_NEAREST),
                             40, 40, 40, 40, cv2.BORDER_CONSTANT, value=255)
    if det.detectAndDecode(big)[0] != url:
        raise SystemExit(f'{name}: 만든 QR 이 원래 주소로 읽히지 않습니다')
    path = os.path.join(OUT, f'support_{name}.png')
    Image.fromarray(m).convert('1').save(path, optimize=True)
    print(path, m.shape[0])

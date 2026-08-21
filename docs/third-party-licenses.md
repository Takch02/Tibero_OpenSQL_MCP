# Third-party licenses

## Apache PDFBox

- Dependency: `org.apache.pdfbox:pdfbox:3.0.8`
- Usage: PDF 텍스트 추출
- License: Apache License 2.0
- Source: <https://pdfbox.apache.org/>

이번 프로젝트는 PDFBox의 기본 텍스트 추출만 사용한다. JBIG2, JPEG 2000 등 선택적 이미지 디코더와 OCR 엔진은 추가하지 않았으며, 이들의 별도 라이선스 검토도 이번 범위에 포함하지 않는다.

## intfloat/multilingual-e5-small

- Usage: 한국어를 포함한 다국어 문서·질의 임베딩
- License: MIT License
- Source: <https://huggingface.co/intfloat/multilingual-e5-small>

ONNX 모델과 토크나이저는 애플리케이션 시작 시 사용자 로컬 캐시에 내려받으며, 저장소와 배포 산출물에는 포함하지 않는다.

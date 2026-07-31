"""보고서 무관 공통 검증 유틸."""
import re


def extract_numbers(text):
    """텍스트의 수치 추출. 앞 글자가 단어문자/점이면 제외(식별자 U2·날짜 하이픈 배제)."""
    pat = r"(?<![\w.])-?\d[\d,]*(?:\.\d+)?"
    return [float(x.replace(",", "")) for x in re.findall(pat, text)]

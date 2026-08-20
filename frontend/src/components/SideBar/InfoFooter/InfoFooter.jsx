import "./InfoFooter.css";

function InfoFooter() {
  return (
    <footer className="login-footer">
      <div className="company-info">
        <div className="logo-placeholder">
          <span className="logo-icon">
            🪐
          </span>

          화성갈22니까
        </div>

        <p>
          (41596) 대구광역시 북구 고성로 141
          KT북대구지사
        </p>

        <p>
          홈페이지 전산 이용 문의 1234-1234
          (평일 09시 - 18시)
        </p>
      </div>

      {/* <div className="footer-links">
        <span className="policy-highlight">
          이용약관
        </span>

        <span className="policy-highlight">
          개인정보처리방침
        </span>

        <span>
          사이트맵
        </span>
      </div> */}
    </footer>
  );
}

export default InfoFooter;
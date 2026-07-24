import "./BladeInfoPopup.css";

function BladeInfoPopup({ selectedBladeName, onClose }) {
  return (
    <div className="blade-info-popup-overlay">
      <div className="blade-info-popup">
        <div className="blade-info-popup-header">
          <strong>블레이드 상세 정보</strong>

          <button
            type="button"
            onClick={onClose}
            aria-label="팝업 닫기"
          >
            ×
          </button>
        </div>

        <div className="blade-info-popup-body">
          <p>
            <span>선택 블레이드</span>
            <strong>{selectedBladeName}</strong>
          </p>

          <p>
            <span>상태</span>
            <strong>점검 대기</strong>
          </p>

          <p>
            <span>확대 모드</span>
            <strong>활성화</strong>
          </p>
        </div>
      </div>
    </div>
  );
}

export default BladeInfoPopup;
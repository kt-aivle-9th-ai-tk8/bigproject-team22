import { useMemo, useState } from "react";
import "./BladeInfoPopup.css";

const PAGE_SIZE = 10;

const BLADE_POSITION_OPTIONS = [
  {
    value: "ALL",
    label: "전체",
  },
  {
    value: "LE",
    label: "LE",
  },
  {
    value: "PS",
    label: "PS",
  },
  {
    value: "SS",
    label: "SS",
  },
  {
    value: "TE",
    label: "TE",
  },
];

const BLADE_POSITION_LABEL = {
  LE: "전연",
  PS: "압력면",
  SS: "흡입면",
  TE: "후연",
};

const SEVERITY_LABEL = {
  1: "낮음",
  2: "보통",
  3: "높음",
  4: "위험",
};

const SEVERITY_CLASS = {
  1: "low",
  2: "medium",
  3: "high",
  4: "critical",
};

const DUMMY_BLADE_HISTORY = [
  {
    id: 1,
    imageUrl: "/images/blade-dummy-1.png",
    bladePosition: "LE",
    defectCount: 3,
    maxSeverity: 3,
    inspectedAt: "2026-07-28",
  },
  {
    id: 2,
    imageUrl: "/images/blade-dummy-2.png",
    bladePosition: "PS",
    defectCount: 1,
    maxSeverity: 1,
    inspectedAt: "2026-07-27",
  },
  {
    id: 3,
    imageUrl: "/images/blade-dummy-3.png",
    bladePosition: "SS",
    defectCount: 5,
    maxSeverity: 4,
    inspectedAt: "2026-07-26",
  },
  {
    id: 4,
    imageUrl: "/images/blade-dummy-4.png",
    bladePosition: "TE",
    defectCount: 2,
    maxSeverity: 2,
    inspectedAt: "2026-07-25",
  },
  {
    id: 5,
    imageUrl: "/images/blade-dummy-5.png",
    bladePosition: "LE",
    defectCount: 0,
    maxSeverity: 1,
    inspectedAt: "2026-07-24",
  },
  {
    id: 6,
    imageUrl: "/images/blade-dummy-6.png",
    bladePosition: "PS",
    defectCount: 4,
    maxSeverity: 3,
    inspectedAt: "2026-07-23",
  },
  {
    id: 7,
    imageUrl: "/images/blade-dummy-7.png",
    bladePosition: "SS",
    defectCount: 2,
    maxSeverity: 2,
    inspectedAt: "2026-07-22",
  },
  {
    id: 8,
    imageUrl: "/images/blade-dummy-8.png",
    bladePosition: "TE",
    defectCount: 6,
    maxSeverity: 4,
    inspectedAt: "2026-07-21",
  },
  {
    id: 9,
    imageUrl: "/images/blade-dummy-9.png",
    bladePosition: "LE",
    defectCount: 1,
    maxSeverity: 1,
    inspectedAt: "2026-07-20",
  },
  {
    id: 10,
    imageUrl: "/images/blade-dummy-10.png",
    bladePosition: "PS",
    defectCount: 3,
    maxSeverity: 2,
    inspectedAt: "2026-07-19",
  },
  {
    id: 11,
    imageUrl: "/images/blade-dummy-11.png",
    bladePosition: "SS",
    defectCount: 7,
    maxSeverity: 4,
    inspectedAt: "2026-07-18",
  },
  {
    id: 12,
    imageUrl: "/images/blade-dummy-12.png",
    bladePosition: "TE",
    defectCount: 2,
    maxSeverity: 2,
    inspectedAt: "2026-07-17",
  },
];

function getTodayString() {
  return new Date().toISOString().slice(0, 10);
}

function getDateStringBefore(days) {
  const date = new Date();

  date.setDate(date.getDate() - days);

  return date.toISOString().slice(0, 10);
}

function BladeInfoPopup({
  selectedBladeName,
  onClose,
  onSelectHistory,
}) {
  const [startDate, setStartDate] = useState(getDateStringBefore(30));
  const [endDate, setEndDate] = useState(getTodayString());
  const [selectedPosition, setSelectedPosition] = useState("ALL");
  const [page, setPage] = useState(1);

  const filteredHistories = useMemo(() => {
    return DUMMY_BLADE_HISTORY.filter((item) => {
      const isInDateRange =
        item.inspectedAt >= startDate &&
        item.inspectedAt <= endDate;

      const isMatchedPosition =
        selectedPosition === "ALL" ||
        item.bladePosition === selectedPosition;

      return isInDateRange && isMatchedPosition;
    });
  }, [startDate, endDate, selectedPosition]);

  const totalPage = Math.max(
    1,
    Math.ceil(filteredHistories.length / PAGE_SIZE)
  );

  const pagedHistories = useMemo(() => {
    const startIndex = (page - 1) * PAGE_SIZE;
    const endIndex = startIndex + PAGE_SIZE;

    return filteredHistories.slice(startIndex, endIndex);
  }, [filteredHistories, page]);

  const handleStartDateChange = (event) => {
    setStartDate(event.target.value);
    setPage(1);
  };

  const handleEndDateChange = (event) => {
    setEndDate(event.target.value);
    setPage(1);
  };

  const handlePositionClick = (position) => {
    setSelectedPosition(position);
    setPage(1);

    console.log("블레이드 위치 필터 변경:", {
      selectedBladeName,
      startDate,
      endDate,
      bladePosition: position,
      page: 0,
      size: PAGE_SIZE,
    });
  };

  const handleHistoryClick = (historyItem) => {
    console.log("블레이드 점검 이력 클릭:", historyItem);

    onSelectHistory?.(historyItem);
  };

  const handlePrevPage = () => {
    setPage((prev) => Math.max(1, prev - 1));
  };

  const handleNextPage = () => {
    setPage((prev) => Math.min(totalPage, prev + 1));
  };

  const getPageNumbers = () => {
    const pages = [];

    for (let pageNumber = 1; pageNumber <= totalPage; pageNumber += 1) {
      pages.push(pageNumber);
    }

    return pages;
  };

  const handlePageClick = (pageNumber) => {
    setPage(pageNumber);
  };

  return (
    <div
      className="blade-info-popup-overlay"
      onClick={onClose}
    >
      <div
        className="blade-info-popup"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="blade-info-popup-header">
          <div>
            <strong>블레이드 점검 이력</strong>
            <span>{selectedBladeName}</span>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="팝업 닫기"
          >
            ×
          </button>
        </div>

        <div className="blade-info-popup-filter">
          <div className="blade-date-filter-row">
            <div className="blade-date-field">
              <label>시작일</label>
              <input
                type="date"
                value={startDate}
                max={endDate}
                onChange={handleStartDateChange}
              />
            </div>

            <span className="blade-date-separator">~</span>

            <div className="blade-date-field">
              <label>종료일</label>
              <input
                type="date"
                value={endDate}
                min={startDate}
                max={getTodayString()}
                onChange={handleEndDateChange}
              />
            </div>
          </div>

          <div className="blade-position-toggle">
            {BLADE_POSITION_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={
                  selectedPosition === option.value ? "active" : ""
                }
                onClick={() => handlePositionClick(option.value)}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        <div className="blade-info-popup-body">
          {pagedHistories.length === 0 ? (
            <div className="blade-history-empty">
              조회된 점검 이력이 없습니다.
            </div>
          ) : (
            pagedHistories.map((item) => {
              const severityLabel =
                SEVERITY_LABEL[item.maxSeverity] || "-";

              const severityClass =
                SEVERITY_CLASS[item.maxSeverity] || "unknown";

              return (
                <button
                  className="blade-history-item"
                  type="button"
                  key={item.id}
                  onClick={() => handleHistoryClick(item)}
                >
                  <div className="blade-history-image">
                    <img
                      src={item.imageUrl}
                      alt={`${selectedBladeName} ${item.bladePosition}`}
                      onError={(event) => {
                        event.currentTarget.style.display = "none";
                      }}
                    />
                    <span>IMAGE</span>
                  </div>

                  <div className="blade-history-content">
                    <div className="blade-history-top">
                      <strong>
                        {item.bladePosition}
                        <span>
                          {BLADE_POSITION_LABEL[item.bladePosition]}
                        </span>
                      </strong>

                      <em>{item.inspectedAt}</em>
                    </div>

                    <div className="blade-history-meta">
                      <p>
                        <span>결함 수</span>
                        <strong>{item.defectCount}개</strong>
                      </p>

                      <p>
                        <span>최대 심각도</span>
                        <strong
                          className={`severity-${severityClass}`}
                        >
                          {severityLabel}
                        </strong>
                      </p>
                    </div>
                  </div>
                </button>
              );
            })
          )}
        </div>

        <div className="blade-info-popup-footer">
          <div className="blade-pagination">
            <button
              className="blade-page-arrow"
              type="button"
              onClick={handlePrevPage}
              disabled={page <= 1}
            >
              {"<"}
            </button>

            {getPageNumbers().map((pageNumber) => (
              <button
                key={pageNumber}
                className={`blade-page-number ${
                  page === pageNumber ? "active" : ""
                }`}
                type="button"
                onClick={() => handlePageClick(pageNumber)}
              >
                {pageNumber}
              </button>
            ))}

            <button
              className="blade-page-arrow"
              type="button"
              onClick={handleNextPage}
              disabled={page >= totalPage}
            >
              {">"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default BladeInfoPopup;
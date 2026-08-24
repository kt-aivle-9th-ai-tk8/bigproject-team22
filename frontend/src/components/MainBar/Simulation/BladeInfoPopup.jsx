import { useMemo, useState } from "react";
import {useDefectImages} from "../../../hooks/useDefectImages";
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
  bladeId,
  bladeTag,
  onClose,
  onSelectHistory,
}) {
  const {
    defectImages,
    isDefectImagesLoading,
    defectImagesError,
  } = useDefectImages({
    bladeId,
  });

  const [
    imageSize,
    setImageSize,
  ] = useState({
    width: 0,
    height: 0,
  });

  const [startDate, setStartDate] = useState(getDateStringBefore(30));
  const [endDate, setEndDate] = useState(getTodayString());
  const [selectedPosition, setSelectedPosition] = useState("ALL");
  const [page, setPage] = useState(1);
  const [selectedImage, setSelectedImage] = useState(null);

  const filteredHistories =
    useMemo(() => {
      return defectImages.filter(
        (item) => {
          const isInDateRange =
            item.inspectedAt >=
              startDate &&
            item.inspectedAt <=
              endDate;

          const isMatchedPosition =
            selectedPosition ===
              "ALL" ||
            item.bladePosition ===
              selectedPosition;

          return (
            isInDateRange &&
            isMatchedPosition
          );
        }
      );
    }, [
      defectImages,
      startDate,
      endDate,
      selectedPosition,
    ]);

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

    
  };

  const handleHistoryClick = (
    historyItem
  ) => {
    setImageSize({
      width: 0,
      height: 0,
    });

    setSelectedImage(
      historyItem
    );

    onSelectHistory?.(
      historyItem
    );
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
          {isDefectImagesLoading ? (
            <div className="blade-history-empty">
              점검 이력을 불러오는 중입니다.
            </div>
          ) : defectImagesError ? (
            <div className="blade-history-empty">
              {defectImagesError}
            </div>
          ) : pagedHistories.length === 0 ? (
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
                      src={item.thumbnailUrl}
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

        {selectedImage && (
          <div
            className="blade-image-preview-overlay"
            onClick={() =>
              setSelectedImage(null)
            }
          >
            <div
              className="blade-image-preview"
              onClick={(event) =>
                event.stopPropagation()
              }
            >
              <button
                className="blade-image-preview-close"
                type="button"
                onClick={() =>
                  setSelectedImage(null)
                }
                aria-label="이미지 확대 닫기"
              >
                ×
              </button>

              <div className="blade-image-preview-canvas">
                <img
                  src={selectedImage.imageUrl}
                  alt={`${selectedBladeName} ${selectedImage.bladePosition}`}
                  onLoad={(event) => {
                    const {
                      naturalWidth,
                      naturalHeight,
                    } = event.currentTarget;

                    setImageSize({
                      width: naturalWidth,
                      height: naturalHeight,
                    });

                    
                  }}
                />

                {imageSize.width > 0 &&
                  imageSize.height > 0 && (
                    <>
                      <svg
                        className="blade-defect-overlay"
                        viewBox={`0 0 ${imageSize.width} ${imageSize.height}`}
                        preserveAspectRatio="none"
                      >
                        {selectedImage.defects?.map(
                          (defect, index) => (
                            <rect
                              key={defect.id ?? index}
                              x={Number(defect.bbox_x)}
                              y={Number(defect.bbox_y)}
                              width={Number(defect.bbox_w)}
                              height={Number(defect.bbox_h)}
                              className="blade-defect-bbox"
                            />
                          )
                        )}
                      </svg>

                      <div className="blade-defect-label-overlay">
                        {selectedImage.defects?.map(
                          (defect, index) => {
                            const bboxRight =
                              Number(defect.bbox_x) +
                              Number(defect.bbox_w);

                            const bboxBottom =
                              Number(defect.bbox_y) +
                              Number(defect.bbox_h);

                            return (
                              <div
                                key={defect.id ?? index}
                                className="blade-defect-label"
                                style={{
                                  left: `${
                                    (bboxRight /
                                      imageSize.width) *
                                    100
                                  }%`,
                                  top: `${
                                    (bboxBottom /
                                      imageSize.height) *
                                    100
                                  }%`,
                                }}
                              >
                                {defect.type}, {defect.severity}
                              </div>
                            );
                          }
                        )}
                      </div>
                    </>
                  )}
              </div>

              <div className="blade-image-preview-info">
                <span>
                  {
                    selectedImage.bladePosition
                  }
                </span>

                <span>
                  {
                    selectedImage.inspectedAt
                  }
                </span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default BladeInfoPopup;
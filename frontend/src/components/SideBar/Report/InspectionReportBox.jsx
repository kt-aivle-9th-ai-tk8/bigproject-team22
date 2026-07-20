import { useRef, useState } from "react";
import "./InspectionReportBox.css";

function InspectionReportBox({ onCreateReport }) {
  const fileInputRef = useRef(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [isDragging, setIsDragging] = useState(false);

  const isValidFile = (file) => {
    if (!file) return false;

    const allowedExtensions = [".zip", ".7z", ".rar"];
    const lowerFileName = file.name.toLowerCase();

    return allowedExtensions.some((extension) =>
      lowerFileName.endsWith(extension)
    );
  };

  const handleFileSelect = (file) => {
  if (!isValidFile(file)) {
    alert("압축 파일만 업로드할 수 있습니다.");
    return;
  }

  setSelectedFile(file);
};

  const handleFileInputChange = (event) => {
    const file = event.target.files?.[0];

    if (!file) return;

    handleFileSelect(file);
  };

  const handleUploadBoxClick = () => {
    fileInputRef.current?.click();
  };

  const handleDragOver = (event) => {
    event.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = (event) => {
    event.preventDefault();
    setIsDragging(false);

    const file = event.dataTransfer.files?.[0];

    if (!file) return;

    handleFileSelect(file);
  };

  const handleRemoveFile = (event) => {
    event.stopPropagation();
    setSelectedFile(null);

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const handleCreateInspectionReport = () => {
    if (!selectedFile) {
      alert("점검 보고서를 생성하려면 압축 파일을 먼저 업로드해주세요.");
      return;
    }

    onCreateReport?.({
      reportKind: "inspection",
      file: selectedFile,
      fileName: selectedFile.name,
      fileSize: selectedFile.size,
      fileType: selectedFile.type,
    });
  };

  return (
    <div className="inspection-report-box">
      <div className="inspection-report-spacer">
        <div
          className={`inspection-upload-box ${isDragging ? "dragging" : ""}`}
          role="button"
          tabIndex={0}
          onClick={handleUploadBoxClick}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === " ") {
              handleUploadBoxClick();
            }
          }}
        >
          <input
            ref={fileInputRef}
            className="inspection-upload-input"
            type="file"
            accept=".zip,.7z,.rar"
            onChange={handleFileInputChange}
          />

          {!selectedFile ? (
            <>
              <div className="inspection-upload-button">
                <span className="inspection-upload-plus">＋</span>
                <span>압축 파일로 시작</span>
              </div>

              <div className="inspection-upload-text">
                이미지가 들어간 압축 파일을 업로드 해주세요
              </div>

              <div className="inspection-upload-sub-text">
                또는 여기에 파일 끌어다 놓기
              </div>
            </>
          ) : (
            <div className="inspection-selected-file">
              <div className="inspection-selected-file-name">
                {selectedFile.name}
              </div>

              <div className="inspection-selected-file-size">
                {(selectedFile.size / 1024 / 1024).toFixed(2)} MB
              </div>

              <button
                className="inspection-file-remove-button"
                type="button"
                onClick={handleRemoveFile}
              >
                삭제
              </button>
            </div>
          )}
        </div>
      </div>

      <button
        className="report-create-button"
        type="button"
        onClick={handleCreateInspectionReport}
      >
        보고서 생성
      </button>

      <button className="report-list-button">
        보고서 목록
      </button>
    </div>
  );
}

export default InspectionReportBox;
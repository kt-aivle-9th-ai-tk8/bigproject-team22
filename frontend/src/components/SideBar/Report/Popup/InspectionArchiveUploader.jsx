import { useRef, useState } from "react";

import "./InspectionArchiveUploader.css";

const BLADE_OPTIONS = [
  {
    id: "Blade_A",
    label: "블레이드 A",
  },
  {
    id: "Blade_B",
    label: "블레이드 B",
  },
  {
    id: "Blade_C",
    label: "블레이드 C",
  },
];

const BLADE_SURFACE_OPTIONS = [
  {
    id: "LE",
    label: "전연",
    englishLabel: "Leading Edge",
  },
  {
    id: "PS",
    label: "압력면",
    englishLabel: "Pressure Side",
  },
  {
    id: "SS",
    label: "흡입면",
    englishLabel: "Suction Side",
  },
  {
    id: "TE",
    label: "후연",
    englishLabel: "Trailing Edge",
  },
];

function BladeToggle({
  selectedBladeId,
  onChangeBlade,
}) {
  return (
    <div className="inspection-blade-toggle">
      {BLADE_OPTIONS.map((blade) => (
        <button
          key={blade.id}
          type="button"
          className={selectedBladeId === blade.id ? "active" : ""}
          onClick={() => onChangeBlade(blade.id)}
        >
          {blade.label}
        </button>
      ))}
    </div>
  );
}

function ImageFileSlot({
  turbineName,
  bladeOption,
  surfaceOption,
  selectedFileData,
  onChangeFiles,
}) {
  const fileInputRef = useRef(null);
  const [isDragging, setIsDragging] = useState(false);

  const selectedImages = selectedFileData?.images || [];

  const isValidImageFile = (file) => {
    if (!file) return false;

    return file.type.startsWith("image/");
  };

  const createImageItem = (file) => {
    return {
      id: `${file.name}-${file.size}-${file.lastModified}`,
      file,
      fileName: file.name,
      fileSize: file.size,
      fileType: file.type,
      previewUrl: URL.createObjectURL(file),
    };
  };

  const handleFilesSelect = (files) => {
    const nextFiles = Array.from(files || []);

    if (nextFiles.length === 0) return;

    const hasInvalidFile = nextFiles.some((file) => !isValidImageFile(file));

    if (hasInvalidFile) {
      alert("이미지 파일만 업로드할 수 있습니다.");
      return;
    }

    const newImageItems = nextFiles.map(createImageItem);

    const imageMap = new Map();

    [...selectedImages, ...newImageItems].forEach((image) => {
      imageMap.set(image.id, image);
    });

    const mergedImages = Array.from(imageMap.values());

    onChangeFiles({
      turbineName,
      bladeId: bladeOption.id,
      bladeLabel: bladeOption.label,
      surfaceId: surfaceOption.id,
      surfaceLabel: surfaceOption.label,
      surfaceEnglishLabel: surfaceOption.englishLabel,
      images: mergedImages,
    });
  };

  const handleUploadBoxClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileInputChange = (event) => {
    handleFilesSelect(event.target.files);
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

    handleFilesSelect(event.dataTransfer.files);
  };

  const handleRemoveImage = (event, imageId) => {
    event.stopPropagation();

    const nextImages = selectedImages.filter((image) => image.id !== imageId);

    onChangeFiles({
      turbineName,
      bladeId: bladeOption.id,
      bladeLabel: bladeOption.label,
      surfaceId: surfaceOption.id,
      surfaceLabel: surfaceOption.label,
      surfaceEnglishLabel: surfaceOption.englishLabel,
      images: nextImages,
    });

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const handleRemoveAll = (event) => {
    event.stopPropagation();

    onChangeFiles({
      turbineName,
      bladeId: bladeOption.id,
      bladeLabel: bladeOption.label,
      surfaceId: surfaceOption.id,
      surfaceLabel: surfaceOption.label,
      surfaceEnglishLabel: surfaceOption.englishLabel,
      images: [],
    });

    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  return (
    <div className="inspection-archive-file-slot">
      <div className="inspection-archive-slot-header">
        <strong>{surfaceOption.label}</strong>
        <span>
          {surfaceOption.englishLabel}, {surfaceOption.id}
        </span>
      </div>

      <div
        className={`inspection-upload-box small ${
          isDragging ? "dragging" : ""
        }`}
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
          accept="image/*"
          multiple
          onChange={handleFileInputChange}
        />

        {selectedImages.length === 0 ? (
          <>
            <div className="inspection-upload-button">
              <span className="inspection-upload-plus">＋</span>
              <span>이미지 업로드</span>
            </div>

            <div className="inspection-upload-sub-text">
              {surfaceOption.label} 촬영 이미지
            </div>
          </>
        ) : (
          <div className="inspection-selected-file">
            <div className="inspection-selected-file-name">
              이미지 {selectedImages.length}개 선택됨
            </div>

            <button
              className="inspection-file-remove-button"
              type="button"
              onClick={handleRemoveAll}
            >
              전체 삭제
            </button>
          </div>
        )}
      </div>

      {selectedImages.length > 0 && (
        <div className="inspection-image-preview">
          <div className="inspection-image-preview-title">
            업로드 이미지 목록
          </div>

          <div className="inspection-image-grid">
            {selectedImages.map((image) => (
              <div className="inspection-image-item" key={image.id}>
                <img
                  src={image.previewUrl}
                  alt={image.fileName}
                />

                <div className="inspection-image-info">
                  <span>{image.fileName}</span>
                  <button
                    type="button"
                    onClick={(event) =>
                      handleRemoveImage(event, image.id)
                    }
                  >
                    삭제
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function InspectionArchiveUploader({
  turbineOptions = [],
  selectedFiles = {},
  onChangeFiles,
}) {
  const [selectedBladeByTurbine, setSelectedBladeByTurbine] = useState({});

  const getSelectedBladeId = (turbineName) => {
    return selectedBladeByTurbine[turbineName] || BLADE_OPTIONS[0].id;
  };

  const handleChangeBlade = (turbineName, bladeId) => {
    setSelectedBladeByTurbine((prev) => ({
      ...prev,
      [turbineName]: bladeId,
    }));
  };

  const handleChangeImages = ({
    turbineName,
    bladeId,
    bladeLabel,
    surfaceId,
    surfaceLabel,
    surfaceEnglishLabel,
    images,
  }) => {
    const fileKey = `${turbineName}-${bladeId}-${surfaceId}`;

    onChangeFiles((prev) => {
      const nextFiles = {
        ...prev,
      };

      if (!images || images.length === 0) {
        delete nextFiles[fileKey];
        return nextFiles;
      }

      nextFiles[fileKey] = {
        turbineName,
        bladeId,
        bladeLabel,
        surfaceId,
        surfaceLabel,
        surfaceEnglishLabel,
        images,
        imageCount: images.length,
      };

      return nextFiles;
    });
  };

  return (
    <div className="inspection-archive-uploader">
      {turbineOptions.map((turbineName) => {
        const selectedBladeId = getSelectedBladeId(turbineName);
        const selectedBladeOption =
          BLADE_OPTIONS.find((blade) => blade.id === selectedBladeId) ||
          BLADE_OPTIONS[0];

        return (
          <div className="inspection-archive-turbine-group" key={turbineName}>
            <div className="inspection-archive-turbine-title">
              {turbineName}
            </div>

            <BladeToggle
              selectedBladeId={selectedBladeId}
              onChangeBlade={(bladeId) =>
                handleChangeBlade(turbineName, bladeId)
              }
            />

            <div className="inspection-archive-surface-grid">
              {BLADE_SURFACE_OPTIONS.map((surfaceOption) => {
                const fileKey = `${turbineName}-${selectedBladeId}-${surfaceOption.id}`;
                const selectedFileData = selectedFiles[fileKey] || null;

                return (
                  <ImageFileSlot
                    key={fileKey}
                    turbineName={turbineName}
                    bladeOption={selectedBladeOption}
                    surfaceOption={surfaceOption}
                    selectedFileData={selectedFileData}
                    onChangeFiles={handleChangeImages}
                  />
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}

export default InspectionArchiveUploader;
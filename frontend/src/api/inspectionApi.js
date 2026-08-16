import { apiFetch } from "./apiClient";

export const createInspection = async ({
  windFarmId,
  inspectionStart,
  inspectionEnd,
  turbines,
  context,
}) => {
  const requestBody = {
    wind_farm_id: windFarmId,
    inspection_start: inspectionStart,
    inspection_end: inspectionEnd,
    turbines,
    context: context || "",
  };

  console.log(
    "POST /api/inspections 요청:",
    requestBody
  );

  const response = await apiFetch("/api/inspections", {
    method: "POST",
    credentials: "include",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(requestBody),
  });

  const responseText = await response.text();

  let responseBody = null;

  try {
    responseBody = responseText
      ? JSON.parse(responseText)
      : null;
  } catch {
    throw new Error(
      "서버에서 JSON이 아닌 응답을 받았습니다."
    );
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "점검 생성을 실패했습니다."
    );
  }

  return responseBody;
};

export const uploadInspectionImage = async ({
  uploadUrl,
  file,
}) => {
  const response = await fetch(uploadUrl, {
    method: "PUT",
    headers: {
      "Content-Type":
        file.type || "application/octet-stream",
    },
    body: file,
  });

  if (!response.ok) {
    throw new Error(
      `이미지 업로드에 실패했습니다. (${file.name})`
    );
  }
};


export const completeInspectionImageUpload = async (
  inspectionId
) => {
  const response = await apiFetch(
    `/api/inspections/${inspectionId}/images-uploaded`,
    {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "application/json",
      },
    }
  );

  const responseText = await response.text();

  let responseBody = null;

  try {
    responseBody = responseText
      ? JSON.parse(responseText)
      : null;
  } catch {
    responseBody = null;
  }

  if (!response.ok) {
    throw new Error(
      responseBody?.message ||
        responseBody?.error ||
        "이미지 업로드 완료 처리에 실패했습니다."
    );
  }

  return responseBody;
};
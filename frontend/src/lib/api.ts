
const API_BASE = "/backend";

export type ColumnDataType =
  | "TEXT"
  | "INTEGER"
  | "BIGINT"
  | "DECIMAL"
  | "BOOLEAN"
  | "DATE"
  | "TIMESTAMP";

export interface ColumnMapping {
  excelColumnName: string;
  databaseColumnName: string;
  dataType: ColumnDataType;
}

export interface ImportMapping {
  datasetName: string;
  columns: ColumnMapping[];
}

async function handleResponse(response: Response) {
  const contentType = response.headers.get("content-type") ?? "";

  const data = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message =
      typeof data === "object" && data !== null
        ? data.error ?? data.message
        : data;

    throw new Error(
      typeof message === "string"
        ? message
        : `Request failed with status ${response.status}`
    );
  }

  return data;
}

export async function previewExcel(file: File) {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${API_BASE}/import/preview`, {
    method: "POST",
    body: formData,
  });

  return handleResponse(response);
}

export async function executeImport(
  file: File,
  mapping: ImportMapping
) {
  const formData = new FormData();

  formData.append("file", file);
  formData.append("mapping", JSON.stringify(mapping));

  const response = await fetch(`${API_BASE}/import/execute`, {
    method: "POST",
    body: formData,
  });

  return handleResponse(response);
}

export async function getDatasets() {
  const response = await fetch(`${API_BASE}/datasets`, {
    method: "GET",
    cache: "no-store",
  });

  return handleResponse(response);
}

export async function getDataset(datasetId: number) {
  const response = await fetch(`${API_BASE}/datasets/${datasetId}`, {
    method: "GET",
    cache: "no-store",
  });

  return handleResponse(response);
}

export async function getDatasetData(
  datasetId: number,
  page = 0,
  pageSize = 10
) {
  const response = await fetch(
    `${API_BASE}/datasets/${datasetId}/data?page=${page}&pageSize=${pageSize}`,
    {
      method: "GET",
      cache: "no-store",
    }
  );

  return handleResponse(response);
}

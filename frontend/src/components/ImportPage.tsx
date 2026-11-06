
"use client";

import { useRef, useState } from "react";
import {
  Upload,
  FileSpreadsheet,
  X,
  CheckCircle2,
  CircleAlert,
  RefreshCw,
  ArrowRight,
} from "lucide-react";

import {
  previewExcel,
  executeImport,
  type ColumnMapping,
  type ColumnDataType,
} from "@/lib/api";

type PreviewData = {
  headers: string[];
  rows: unknown[][];
  totalRows: number;
  totalColumns: number;
  sheetName: string;
};

const DATA_TYPES: ColumnDataType[] = [
  "TEXT",
  "INTEGER",
  "BIGINT",
  "DECIMAL",
  "BOOLEAN",
  "DATE",
  "TIMESTAMP",
];

function normalizePreview(data: unknown): PreviewData {
  const payload = data as Record<string, unknown>;

  const rawHeaders =
    payload.headers ??
    payload.columnHeaders ??
    payload.columns ??
    [];

  const headers = Array.isArray(rawHeaders)
    ? rawHeaders.map((item) => {
        if (typeof item === "string") return item;

        const column = item as Record<string, unknown>;

        return String(
          column.excelColumnName ??
          column.header ??
          column.name ??
          column.columnName ??
          ""
        );
      })
    : [];

  const rawRows =
    payload.rows ??
    payload.previewRows ??
    payload.data ??
    [];

  const rows = Array.isArray(rawRows)
    ? rawRows.map((row) => {
        if (Array.isArray(row)) return row;

        if (row && typeof row === "object") {
          const record = row as Record<string, unknown>;
          return headers.map((header) => record[header] ?? "");
        }

        return [row];
      })
    : [];

  return {
    headers,
    rows,
    totalRows: Number(
      payload.totalRows ?? payload.totalRowCount ?? rows.length
    ),
    totalColumns: Number(
      payload.totalColumns ?? payload.columnCount ?? headers.length
    ),
    sheetName: String(payload.sheetName ?? "First worksheet"),
  };
}

function toDatabaseColumnName(value: string) {
  return value
    .trim()
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

export default function ImportPage() {
  const inputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [datasetName, setDatasetName] = useState("");
  const [preview, setPreview] = useState<PreviewData | null>(null);
  const [mappings, setMappings] = useState<ColumnMapping[]>([]);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [imported, setImported] = useState(false);
  const [dragging, setDragging] = useState(false);

  function chooseFile(selected?: File) {
    if (!selected) return;

    if (!/\.(xlsx|xls)$/i.test(selected.name)) {
      setError("Please select a valid .xlsx or .xls Excel file.");
      return;
    }

    setFile(selected);
    setDatasetName(selected.name.replace(/\.(xlsx|xls)$/i, ""));
    setPreview(null);
    setMappings([]);
    setMessage("");
    setError("");
    setImported(false);
  }

  async function handlePreview() {
    if (!file) {
      setError("Select an Excel file first.");
      return;
    }

    setLoading(true);
    setError("");
    setMessage("");

    try {
      const response = await previewExcel(file);
      const result = normalizePreview(response);

      if (result.headers.length === 0) {
        throw new Error(
          "The backend did not return any column headers. Check the Excel file and preview response."
        );
      }

      setPreview(result);

      setMappings(
        result.headers.map((header) => ({
          excelColumnName: header,
          databaseColumnName: toDatabaseColumnName(header),
          dataType: "TEXT",
        }))
      );

      setMessage("Excel preview loaded successfully.");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Unable to preview Excel file."
      );
    } finally {
      setLoading(false);
    }
  }

  function updateMapping(
    index: number,
    field: keyof ColumnMapping,
    value: string
  ) {
    setMappings((current) =>
      current.map((mapping, i) =>
        i === index
          ? { ...mapping, [field]: value }
          : mapping
      )
    );
  }

  async function handleImport() {
    if (!file || !preview) {
      setError("Preview the Excel file before importing.");
      return;
    }

    if (!datasetName.trim()) {
      setError("Enter a dataset name.");
      return;
    }

    if (
      mappings.some(
        (mapping) => !mapping.databaseColumnName.trim()
      )
    ) {
      setError("Every Excel column needs a database column name.");
      return;
    }

    const normalizedNames = mappings.map((mapping) =>
      mapping.databaseColumnName.trim().toLowerCase()
    );

    if (new Set(normalizedNames).size !== normalizedNames.length) {
      setError("Database column names must be unique.");
      return;
    }

    setLoading(true);
    setError("");
    setMessage("");

    try {
      const result = await executeImport(file, {
        datasetName: datasetName.trim(),
        columns: mappings,
      });

      setImported(true);

      const response = result as Record<string, unknown>;

      setMessage(
        `Import completed successfully. ${
          response.totalRows ?? response.importedRows ?? preview.totalRows
        } records processed.`
      );
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Excel import failed."
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <div className="module-heading">
        <div>
          <h1>Import data</h1>
          <p>Bring your Excel data into AF Data Studio.</p>
        </div>
      </div>

      <div className="module-grid">
        <section className="surface import-main">
          <div className="module-section-title">
            <FileSpreadsheet size={21} />
            <div>
              <h2>Upload Excel file</h2>
              <p>Import .xlsx and .xls files into your workspace.</p>
            </div>
          </div>

          <div
            className="upload-zone"
            style={{
              borderColor: dragging ? "#D76E46" : undefined,
              cursor: "pointer",
            }}
            onClick={() => inputRef.current?.click()}
            onDragOver={(event) => {
              event.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              event.preventDefault();
              setDragging(false);
              chooseFile(event.dataTransfer.files[0]);
            }}
          >
            <Upload size={34} />
            <h3>Drag and drop your Excel file here</h3>
            <p>or click below to browse your computer</p>
            <button
              type="button"
              className="import-button"
              onClick={(event) => {
                event.stopPropagation();
                inputRef.current?.click();
              }}
            >
              BROWSE FILES
            </button>
            <small>Supported formats: XLSX, XLS</small>
          </div>

          <input
            ref={inputRef}
            hidden
            type="file"
            accept=".xlsx,.xls"
            onChange={(event) => chooseFile(event.target.files?.[0])}
          />

          {file && (
            <div className="selected-file">
              <FileSpreadsheet size={22} />
              <div>
                <strong>{file.name}</strong>
                <small>
                  {(file.size / 1024).toFixed(1)} KB · Excel file
                </small>
              </div>
              <CheckCircle2 color="#62806b" />
              <button
                type="button"
                onClick={() => {
                  setFile(null);
                  setPreview(null);
                  setMappings([]);
                  setMessage("");
                  setError("");
                  setImported(false);
                  if (inputRef.current) inputRef.current.value = "";
                }}
                aria-label="Remove selected file"
              >
                <X size={17} />
              </button>
            </div>
          )}

          {file && (
            <div className="analysis-action">
              <button
                type="button"
                className="import-button"
                disabled={loading}
                onClick={handlePreview}
              >
                {loading ? (
                  <><RefreshCw size={16} className="animate-spin" /> READING FILE</>
                ) : (
                  <><ArrowRight size={16} /> PREVIEW EXCEL DATA</>
                )}
              </button>
            </div>
          )}

          {message && (
            <p className="feedback-message">
              <CheckCircle2 size={16} /> {message}
            </p>
          )}

          {error && (
            <p className="feedback-message" style={{ color: "#b64b3d" }}>
              <CircleAlert size={16} /> {error}
            </p>
          )}

          <div className="info-strip">
            <CircleAlert size={17} />
            <span>
              Your Excel file is sent to the backend only when you request a
              preview or execute the import.
            </span>
          </div>
        </section>

        <section className="surface">
          <h2>Import workflow</h2>
          <div className="workflow-list">
            {[
              "Select Excel file",
              "Read column headers",
              "Preview and validate data",
              "Map Excel columns",
              "Import records into PostgreSQL",
            ].map((step, index) => {
              const completed =
                index === 0 ? !!file :
                index === 1 || index === 2 ? !!preview :
                index === 3 ? mappings.length > 0 && !!preview :
                imported;

              return (
                <div className="workflow-step" key={step}>
                  <span>
                    {completed ? <CheckCircle2 size={16} /> : index + 1}
                  </span>
                  <div>
                    <strong>{step}</strong>
                    <small>
                      {completed ? "Completed" : "Waiting for previous step"}
                    </small>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      </div>

      {preview && (
        <>
          <section className="surface full-panel">
            <div className="section-heading">
              <div>
                <h2>Excel preview</h2>
                <p>
                  Sheet: {preview.sheetName} · {preview.totalRows} records ·{" "}
                  {preview.totalColumns} columns
                </p>
              </div>
              <span className="status-pill ready">PREVIEW READY</span>
            </div>

            <div className="form-grid">
              <label>
                DATASET NAME
                <input
                  value={datasetName}
                  onChange={(event) => setDatasetName(event.target.value)}
                  placeholder="Enter dataset name"
                />
              </label>
            </div>

            <div className="data-table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    {preview.headers.map((header, index) => (
                      <th key={`${header}-${index}`}>{header}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {preview.rows.slice(0, 10).map((row, rowIndex) => (
                    <tr key={rowIndex}>
                      {preview.headers.map((_, columnIndex) => (
                        <td key={columnIndex}>
                          {String(row[columnIndex] ?? "")}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <small>
              Showing up to 10 preview rows. The backend determines the
              complete import row count.
            </small>
          </section>

          <section className="surface full-panel">
            <h2>Column mapping</h2>
            <p className="panel-description">
              Map each Excel header to a PostgreSQL column and select its
              expected data type.
            </p>

            <div className="data-table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>EXCEL COLUMN</th>
                    <th>DATABASE COLUMN</th>
                    <th>DATA TYPE</th>
                  </tr>
                </thead>
                <tbody>
                  {mappings.map((mapping, index) => (
                    <tr key={`${mapping.excelColumnName}-${index}`}>
                      <td><strong>{mapping.excelColumnName}</strong></td>
                      <td>
                        <input
                          value={mapping.databaseColumnName}
                          onChange={(event) =>
                            updateMapping(
                              index,
                              "databaseColumnName",
                              event.target.value
                            )
                          }
                        />
                      </td>
                      <td>
                        <select
                          value={mapping.dataType}
                          onChange={(event) =>
                            updateMapping(
                              index,
                              "dataType",
                              event.target.value
                            )
                          }
                        >
                          {DATA_TYPES.map((type) => (
                            <option key={type} value={type}>{type}</option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="analysis-action">
              <button
                type="button"
                className="import-button"
                disabled={loading || imported}
                onClick={handleImport}
              >
                {loading ? (
                  <><RefreshCw size={16} /> IMPORTING DATA</>
                ) : imported ? (
                  <><CheckCircle2 size={16} /> IMPORT COMPLETED</>
                ) : (
                  <><Upload size={16} /> CREATE DATASET & IMPORT</>
                )}
              </button>
            </div>
          </section>
        </>
      )}
    </>
  );
}

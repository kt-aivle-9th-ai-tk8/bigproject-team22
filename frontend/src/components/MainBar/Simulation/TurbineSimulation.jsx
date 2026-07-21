import { useState } from "react";
import "./TurbineSimulation.css";

function TurbineSimulation({
  plantName,
  turbineName,
  onRunSimulation,
}) {
  const [failureType, setFailureType] = useState("bearing");
  const [downtimeHour, setDowntimeHour] = useState(12);
  const [outputRate, setOutputRate] = useState(75);
  const [memo, setMemo] = useState("");
  const [result, setResult] = useState(null);

  const handleRunSimulation = () => {
    const simulationData = {
      simulationType: "turbine-repair-impact",
      plantName,
      turbineName,
      failureType,
      expectedDowntimeHour: Number(downtimeHour),
      targetOutputRate: Number(outputRate),
      memo,
    };

    const expectedLossRate = 100 - Number(outputRate);
    const expectedLossPower = (expectedLossRate / 100) * Number(downtimeHour);

    const simulationResult = {
      expectedLossRate,
      expectedLossPower: expectedLossPower.toFixed(2),
    };

    setResult(simulationResult);

    onRunSimulation?.({
      ...simulationData,
      result: simulationResult,
    });
  };

  return (
    <div className="turbine-simulation">
      <div className="simulation-left-panel">
        <h3 className="simulation-title">터빈 시뮬레이션 조건</h3>

        <div className="simulation-form-grid">
          <div className="simulation-field">
            <label>고장 유형</label>
            <select
              value={failureType}
              onChange={(event) => setFailureType(event.target.value)}
            >
              <option value="bearing">베어링 이상</option>
              <option value="blade">블레이드 손상</option>
              <option value="generator">발전기 이상</option>
              <option value="sensor">센서 이상</option>
            </select>
          </div>

          <div className="simulation-field">
            <label>예상 정지 시간</label>
            <input
              type="number"
              min="0"
              value={downtimeHour}
              onChange={(event) => setDowntimeHour(event.target.value)}
            />
          </div>

          <div className="simulation-field">
            <label>목표 출력률</label>
            <input
              type="number"
              min="0"
              max="100"
              value={outputRate}
              onChange={(event) => setOutputRate(event.target.value)}
            />
          </div>

          <div className="simulation-field simulation-full-field">
            <label>메모</label>
            <textarea
              value={memo}
              placeholder="시뮬레이션 조건 메모를 입력하세요"
              onChange={(event) => setMemo(event.target.value)}
            />
          </div>
        </div>

        <button
          className="simulation-run-button"
          type="button"
          onClick={handleRunSimulation}
        >
          시뮬레이션 실행
        </button>
      </div>

      <div className="simulation-right-panel">
        <h3 className="simulation-title">시뮬레이션 결과</h3>

        {!result ? (
          <div className="simulation-empty-result">
            시뮬레이션 실행 후 결과가 표시됩니다.
          </div>
        ) : (
          <div className="simulation-result-card">
            <div className="simulation-result-row">
              <span>예상 출력 손실률</span>
              <strong>{result.expectedLossRate}%</strong>
            </div>

            <div className="simulation-result-row">
              <span>예상 손실 지표</span>
              <strong>{result.expectedLossPower}</strong>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default TurbineSimulation;
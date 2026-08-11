import { useEffect, useMemo, useRef, useState } from "react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
} from "recharts";

import "./PowerChartDrag.css";

const TWO_HOUR_MS = 2 * 60 * 60 * 1000;

const DEFAULT_VISIBLE_TICK_COUNT = 10;
const DEFAULT_MIN_VISIBLE_TICK_COUNT = 10;
const DEFAULT_MAX_VISIBLE_TICK_COUNT = 30;

function formatTickLabel(timestamp) {
  const date = new Date(timestamp);

  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hour = String(date.getHours()).padStart(2, "0");

  return `${month}/${day} ${hour}:00`;
}

function formatRangeLabel(timestamp) {
  const date = new Date(timestamp);

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hour = String(date.getHours()).padStart(2, "0");
  const minute = String(date.getMinutes()).padStart(2, "0");

  return `${year}-${month}-${day} ${hour}:${minute}`;
}

function floorToTwoHour(timestamp) {
  return Math.floor(timestamp / TWO_HOUR_MS) * TWO_HOUR_MS;
}

function ceilToTwoHour(timestamp) {
  return Math.ceil(timestamp / TWO_HOUR_MS) * TWO_HOUR_MS;
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function createTimeTicks(axisStartAt, axisEndAt) {
  const startTime = ceilToTwoHour(new Date(axisStartAt).getTime());
  const endTime = floorToTwoHour(new Date(axisEndAt).getTime());

  const ticks = [];
  let currentTime = startTime;

  while (currentTime <= endTime) {
    ticks.push(currentTime);
    currentTime += TWO_HOUR_MS;
  }

  return ticks;
}

function PowerChartDrag({
  data = [],
  axisStartAt,
  axisEndAt,

  visibleTickCount = DEFAULT_VISIBLE_TICK_COUNT,
  minVisibleTickCount = DEFAULT_MIN_VISIBLE_TICK_COUNT,
  maxVisibleTickCount = DEFAULT_MAX_VISIBLE_TICK_COUNT,

  mode,
  selectedPlant,
  selectedTurbine,

  isLoading = false,
  onRangeDrag,
}) {
  const dragInfoRef = useRef({
    isDragging: false,
    startX: 0,
    tickWidth: 0,
    startVisibleIndex: 0,
  });

  const hasInitialRangeEmittedRef = useRef(false);

  const normalizedInitialVisibleTickCount = clamp(
    visibleTickCount,
    minVisibleTickCount,
    maxVisibleTickCount
  );

  const [currentVisibleTickCount, setCurrentVisibleTickCount] = useState(
    normalizedInitialVisibleTickCount
  );

  const [visibleStartIndex, setVisibleStartIndex] = useState(0);
  const [previewVisibleStartIndex, setPreviewVisibleStartIndex] = useState(0);
  const [isDragging, setIsDragging] = useState(false);

  const xAxisTicks = useMemo(() => {
    if (!axisStartAt || !axisEndAt) return [];

    return createTimeTicks(axisStartAt, axisEndAt);
  }, [axisStartAt, axisEndAt]);

  const maxVisibleStartIndex = Math.max(
    0,
    xAxisTicks.length - currentVisibleTickCount
  );

  useEffect(() => {
    setCurrentVisibleTickCount(normalizedInitialVisibleTickCount);
  }, [normalizedInitialVisibleTickCount]);

  const chartData = useMemo(() => {
    return data.map((item) => {
      const timestamp = new Date(item.measuredAt).getTime();

      return {
        ...item,
        timestamp,
        powerGeneration: Number(item.powerGeneration || 0),
      };
    });
  }, [data]);

  /*
   * 현재 API로 받아온 data의 시간 범위
   * 휠 축소 시, 새 화면 범위가 이 범위를 벗어나면 API 호출
   */
  const loadedDataRange = useMemo(() => {
    if (chartData.length === 0) {
      return {
        startTime: null,
        endTime: null,
      };
    }

    const timestamps = chartData.map((item) => item.timestamp);

    return {
      startTime: Math.min(...timestamps),
      endTime: Math.max(...timestamps),
    };
  }, [chartData]);

  const getVisibleTicksByIndex = (startIndex, tickCount) => {
    return xAxisTicks.slice(
      startIndex,
      Math.min(xAxisTicks.length, startIndex + tickCount)
    );
  };

  const getVisibleRangeByIndex = (startIndex, tickCount) => {
    const nextVisibleTicks = getVisibleTicksByIndex(startIndex, tickCount);

    if (nextVisibleTicks.length === 0) {
      return null;
    }

    const nextStartTime = nextVisibleTicks[0];
    const nextEndTime = nextVisibleTicks[nextVisibleTicks.length - 1];

    return {
      nextStartAt: new Date(nextStartTime),
      nextEndAt: new Date(nextEndTime),
      visibleTicks: nextVisibleTicks,
      visibleTickCount: tickCount,
    };
  };

  const emitVisibleRange = (nextVisibleStartIndex, nextVisibleTickCount) => {
    const rangePayload = getVisibleRangeByIndex(
      nextVisibleStartIndex,
      nextVisibleTickCount
    );

    if (!rangePayload) return;

    onRangeDrag?.(rangePayload);
  };

  const isRangeCoveredByLoadedData = (nextVisibleStartIndex, nextVisibleTickCount) => {
    const rangePayload = getVisibleRangeByIndex(
      nextVisibleStartIndex,
      nextVisibleTickCount
    );

    if (!rangePayload) return true;

    if (
      loadedDataRange.startTime === null ||
      loadedDataRange.endTime === null
    ) {
      return false;
    }

    const nextStartTime = rangePayload.nextStartAt.getTime();
    const nextEndTime = rangePayload.nextEndAt.getTime();

    return (
      nextStartTime >= loadedDataRange.startTime &&
      nextEndTime <= loadedDataRange.endTime
    );
  };

  /*
   * axis 범위가 바뀌면 가장 최근 구간부터 보여줌
   */
  useEffect(() => {
    const nextMaxVisibleStartIndex = Math.max(
      0,
      xAxisTicks.length - currentVisibleTickCount
    );

    setVisibleStartIndex(nextMaxVisibleStartIndex);
    setPreviewVisibleStartIndex(nextMaxVisibleStartIndex);
    hasInitialRangeEmittedRef.current = false;
  }, [xAxisTicks.length]);

  /*
   * 최초 axis 생성 시 1회만 호출
   */
  useEffect(() => {
    if (xAxisTicks.length === 0) return;
    if (hasInitialRangeEmittedRef.current) return;

    const nextMaxVisibleStartIndex = Math.max(
      0,
      xAxisTicks.length - currentVisibleTickCount
    );

    hasInitialRangeEmittedRef.current = true;

    emitVisibleRange(nextMaxVisibleStartIndex, currentVisibleTickCount);
  }, [xAxisTicks.length]);

  const visibleTicks = useMemo(() => {
    return getVisibleTicksByIndex(
      previewVisibleStartIndex,
      currentVisibleTickCount
    );
  }, [xAxisTicks, previewVisibleStartIndex, currentVisibleTickCount]);

  const visibleDomain = useMemo(() => {
    if (visibleTicks.length === 0) {
      const now = Date.now();
      return [now, now];
    }

    const firstTick = visibleTicks[0];
    const lastTick = visibleTicks[visibleTicks.length - 1];

    return [firstTick, lastTick];
  }, [visibleTicks]);

  const handlePointerDown = (event) => {
    if (isLoading) return;
    if (xAxisTicks.length <= currentVisibleTickCount) return;

    const chartWidth = event.currentTarget.clientWidth;
    const tickWidth = chartWidth / Math.max(1, currentVisibleTickCount - 1);

    dragInfoRef.current = {
      isDragging: true,
      startX: event.clientX,
      tickWidth,
      startVisibleIndex: visibleStartIndex,
    };

    setIsDragging(true);

    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handlePointerMove = (event) => {
    const dragInfo = dragInfoRef.current;

    if (!dragInfo.isDragging) return;

    const deltaX = event.clientX - dragInfo.startX;

    /*
     * 오른쪽 드래그: 과거 방향
     * 왼쪽 드래그: 미래 방향
     */
    const movedTickCount = Math.round(deltaX / dragInfo.tickWidth);

    const nextVisibleStartIndex = clamp(
      dragInfo.startVisibleIndex - movedTickCount,
      0,
      maxVisibleStartIndex
    );

    setPreviewVisibleStartIndex(nextVisibleStartIndex);
  };

  const handlePointerUp = (event) => {
    const dragInfo = dragInfoRef.current;

    if (!dragInfo.isDragging) return;

    dragInfoRef.current.isDragging = false;
    setIsDragging(false);

    try {
      event.currentTarget.releasePointerCapture(event.pointerId);
    } catch {
      // pointer capture 예외 방지
    }

    const nextVisibleStartIndex = previewVisibleStartIndex;

    const isXAxisChanged = nextVisibleStartIndex !== visibleStartIndex;

    setVisibleStartIndex(nextVisibleStartIndex);

    /*
     * 드래그 종료 후 x축 시작 위치가 실제로 바뀐 경우에만 호출
     */
    if (isXAxisChanged) {
      emitVisibleRange(nextVisibleStartIndex, currentVisibleTickCount);
    }
  };

  const handlePointerCancel = () => {
    dragInfoRef.current.isDragging = false;
    setIsDragging(false);
    setPreviewVisibleStartIndex(visibleStartIndex);
  };

  const handleWheel = (event) => {
    if (isLoading) return;
    if (xAxisTicks.length === 0) return;

    event.preventDefault();

    /*
     * wheel up: 확대, 점 개수 감소
     * wheel down: 축소, 점 개수 증가
     */
    const isZoomIn = event.deltaY < 0;
    const isZoomOut = event.deltaY > 0;

    const nextVisibleTickCount = clamp(
      currentVisibleTickCount + (isZoomIn ? -1 : 1),
      minVisibleTickCount,
      Math.min(maxVisibleTickCount, xAxisTicks.length)
    );

    if (nextVisibleTickCount === currentVisibleTickCount) return;

    const chartRect = event.currentTarget.getBoundingClientRect();
    const mouseX = event.clientX - chartRect.left;
    const mouseRatio = clamp(mouseX / chartRect.width, 0, 1);

    /*
     * 마우스 위치를 기준으로 확대/축소
     */
    const anchorTickIndex =
      visibleStartIndex + mouseRatio * (currentVisibleTickCount - 1);

    const nextMaxVisibleStartIndex = Math.max(
      0,
      xAxisTicks.length - nextVisibleTickCount
    );

    const nextVisibleStartIndex = clamp(
      Math.round(anchorTickIndex - mouseRatio * (nextVisibleTickCount - 1)),
      0,
      nextMaxVisibleStartIndex
    );

    setCurrentVisibleTickCount(nextVisibleTickCount);
    setVisibleStartIndex(nextVisibleStartIndex);
    setPreviewVisibleStartIndex(nextVisibleStartIndex);

    /*
     * 휠 확대: API 호출 안 함
     */
    if (isZoomIn) return;

    /*
     * 휠 축소:
     * 축소 후 새 화면 범위가 현재 API로 받아온 data 범위를 벗어날 때만 호출
     */
    if (isZoomOut) {
      const isCovered = isRangeCoveredByLoadedData(
        nextVisibleStartIndex,
        nextVisibleTickCount
      );

      if (!isCovered) {
        emitVisibleRange(nextVisibleStartIndex, nextVisibleTickCount);
      }
    }
  };

  const isEmptyAxis = xAxisTicks.length === 0;

  return (
    <div className="power-chart-box">
      <div className="power-chart-header">
        <div className="power-chart-title">
          <strong>
            {mode === "turbine"
              ? `${selectedTurbine?.name || ""} 터빈 시간별 발전량`
              : `${selectedPlant?.name || ""} 시간별 발전량`}
          </strong>

          <span>
            {isEmptyAxis
              ? "-"
              : `${formatRangeLabel(visibleDomain[0])} ~ ${formatRangeLabel(
                  visibleDomain[1]
                )}`}
          </span>
        </div>
      </div>

      <div
        className={`power-chart-area ${isDragging ? "dragging" : ""}`}
        onWheel={handleWheel}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerCancel}
        onPointerLeave={handlePointerCancel}
      >
        {isEmptyAxis ? (
          <div className="power-chart-loading">
            표시할 x축 범위가 없습니다.
          </div>
        ) : (
          <>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart
                data={isLoading ? [] : chartData}
                margin={{
                  top: 12,
                  right: 18,
                  left: -10,
                  bottom: 0,
                }}
              >
                <XAxis
                  type="number"
                  dataKey="timestamp"
                  domain={visibleDomain}
                  ticks={visibleTicks}
                  tickFormatter={formatTickLabel}
                  scale="time"
                  allowDataOverflow
                  tick={{
                    fontSize: 10,
                  }}
                />

                <YAxis
                  tick={{
                    fontSize: 10,
                  }}
                />

                <Tooltip
                  formatter={(value) => {
                    const numberValue = Number(value);

                    const formattedValue =
                      numberValue >= 100
                        ? `${(numberValue / 1000).toFixed(2).replace(/\.?0+$/, "")} MWh`
                        : `${numberValue.toFixed(2).replace(/\.?0+$/, "")} kWh`;

                    return [formattedValue, "발전량"];
                  }}
                  labelFormatter={(label) => `시간: ${formatTickLabel(label)}`}
                  contentStyle={{
                    padding: "4px 6px",
                    fontSize: "12px",
                    borderRadius: "5px",
                  }}
                  labelStyle={{
                    fontSize: "12px",
                    marginBottom: "0",
                  }}
                  itemStyle={{
                    fontSize: "12px",
                    padding: "0",
                    color: "var(--color-text-point-dark)",
                  }}
                />

                {!isLoading && (
                  <Line
                    type="monotone"
                    dataKey="powerGeneration"
                    stroke="var(--color-point)"
                    strokeWidth={3}
                    dot={false}
                    activeDot={{
                      r: 4,
                      fill: "var(--color-text-point-dark)",
                      stroke: "#ffffff",
                      strokeWidth: 1,
                    }}
                    isAnimationActive={false}
                  />
                )}
              </LineChart>
            </ResponsiveContainer>

            {isLoading && (
              <div className="power-chart-plot-loading">
                발전량 데이터를 불러오는 중입니다.
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

export default PowerChartDrag;
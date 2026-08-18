import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';
import './AdminUserScreen.css';

import { useAdminUsers } from "../hooks/useAdminUsers";
import { useForceLogoutUser } from "../hooks/useForceLogoutUser";
import { useUpdateAdminUser } from "../hooks/useUpdateAdminUser";
import { useApproveAdminUser } from "../hooks/useApproveAdminUser";
import { useWindFarmOptions } from "../hooks/useWindFarmOptions";

export default function AdminUserScreen() {
  const [activeTab, setActiveTab] = useState('pending'); // 'pending' | 'list'
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(false);

  // 정렬 및 필터 상태
  const [sortOrder, setSortOrder] = useState('asc');
  const [selectedPlantFilter, setSelectedPlantFilter] = useState('ALL');

  // 모달 상태
  const [selectedUserForApproval, setSelectedUserForApproval] = useState(null);
  const [selectedPlants, setSelectedPlants] = useState([]);
  const [openDropdownId, setOpenDropdownId] = useState(null);
  const [errorMessageModal, setErrorMessageModal] = useState(null);

  const {
    users: pendingUsers,
    loading: pendingUsersLoading,
    error: pendingUsersError,
    refetch: refetchPendingUsers,
  } = useAdminUsers({
    role: "GUEST",
  });

  const {
    users: approvedUsers,
    loading: adminUsersLoading,
    error: adminUsersError,
    refetch: refetchAdminUsers,
  } = useAdminUsers();

  const {
    forceLogoutUser,
    isForceLoggingOut,
  } = useForceLogoutUser();

  const {
    updateUser,
    isUpdatingUser,
  } = useUpdateAdminUser();

  const {
    approveUser,
    isApproving,
  } = useApproveAdminUser();

  const {
    windFarms,
    loading: windFarmsLoading,
    error: windFarmsError,
  } = useWindFarmOptions();

  // --- [공통 백엔드 에러 추출 함수] ---
  const handleApiError = (err, fallbackMsg) => {
    console.error(err);
    const serverMessage = err.response?.data?.message || err.message || fallbackMsg;
    setErrorMessageModal(serverMessage);
  };

  // --- [정렬 및 필터링 계산] ---
  const processedApprovedUsers = useMemo(() => {
    let result = [...approvedUsers];

    if (searchTerm) {
      result = result.filter(u => 
        u.name.includes(searchTerm) || u.employeeId.includes(searchTerm)
      );
    }

    if (selectedPlantFilter !== 'ALL') {
      result = result.filter(u => u.plants.includes(selectedPlantFilter));
    }

    result.sort((a, b) => {
      if (sortOrder === 'asc') {
        return a.name.localeCompare(b.name, 'ko');
      } else {
        return b.name.localeCompare(a.name, 'ko');
      }
    });

    return result;
  }, [approvedUsers, searchTerm, selectedPlantFilter, sortOrder]);

  // --- [이벤트 핸들러] ---
  const handleOpenApproveModal = (user) => {
    setSelectedUserForApproval(user);
    setSelectedPlants([]);
  };

  const handleCloseModal = () => {
    setSelectedUserForApproval(null);
    setSelectedPlants([]);
  };

  const handleTogglePlantInModal = (
    windFarmId
  ) => {
    setSelectedPlants((prev) =>
      prev.includes(windFarmId)
        ? prev.filter(
            (id) => id !== windFarmId
          )
        : [...prev, windFarmId]
    );
  };

  const handleConfirmApproval = async () => {
    try {
      await approveUser({
        userId: selectedUserForApproval.id,
        windFarmIds: selectedPlants,
      });

      alert(
        `${selectedUserForApproval.name} 님의 가입이 승인되었습니다.`
      );

      handleCloseModal();

      refetchPendingUsers();
      refetchAdminUsers();
    } catch (err) {
      handleApiError(
        err,
        "승인 처리에 실패했습니다."
      );
    }
  };
  
  // --- [3. 가입 거절 - PATCH 또는 DELETE] ---
  // const handleReject = async (id, name) => {
  //   if (window.confirm(`${name} 님의 가입 신청을 거절하시겠습니까?`)) {
  //     try {
  //       await axios.patch(`/api/admin/users/${id}`, { role: 'REJECTED' });
  //       alert('거절 처리 되었습니다.');
  //       refetchPendingUsers();
  //     } catch (err) {
  //       handleApiError(err, '가입 거절 처리에 실패했습니다.');
  //     }
  //   }
  // };
  const handleReject = () => {
    alert("가입 거절 기능은 준비 중입니다.");
  };

  const handleForceLogout = async (userId, name) => {
    try {
      await forceLogoutUser(userId);

      alert(
        `${name} 님을 강제 로그아웃 시켰습니다.`
      );

      refetchAdminUsers();
    } catch (err) {
      handleApiError(
        err,
        "강제 로그아웃 처리에 실패했습니다."
      );
    }
  };

  const handleUnblockUser = async (id, name) => {
    if (window.confirm(`${name} 님의 로그인 차단을 해제하시겠습니까?`)) {
      try {
        await axios.patch(`/api/admin/users/${id}`, {
          is_blocked: false,
          login_fail_count: 0
        });
        alert('차단이 해제되었습니다.');
        refetchAdminUsers();
      } catch (err) {
        handleApiError(err, '차단 해제 처리에 실패했습니다.');
      }
    }
  };

  const handleTogglePlantInList = async (
    userId,
    plantOption
  ) => {
    const targetUser =
      approvedUsers.find(
        (user) => user.id === userId
      );

    if (!targetUser) return;

    const windFarmId =
      plantOption.id;

    if (!windFarmId) {
      setErrorMessageModal(
        "발전소 ID가 없습니다."
      );
      return;
    }

    const currentWindFarmIds =
      (targetUser.assignments || []).map(
        (assignment) =>
          assignment.wind_farm_id
      );

    const exists =
      currentWindFarmIds.includes(
        windFarmId
      );

    const updatedWindFarmIds = exists
      ? currentWindFarmIds.filter(
          (id) => id !== windFarmId
        )
      : [
          ...currentWindFarmIds,
          windFarmId,
        ];

    try {
      await updateUser({
        userId: targetUser.id,
        role: targetUser.role,
        windFarmIds:
          updatedWindFarmIds,
      });

      refetchAdminUsers();
    } catch (err) {
      handleApiError(
        err,
        "발전소 설정 변경에 실패했습니다."
      );
    }
  };

  const filteredPending = pendingUsers.filter(u => 
    u.name.includes(searchTerm) || u.employeeId.includes(searchTerm)
  );

  return (
    <div className="aus-container">
      {/* 헤더 */}
      <header className="aus-header">
        <h1 className="aus-title">사용자 관리</h1>
        <div className="aus-profile-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#666" strokeWidth="1.5">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
            <circle cx="12" cy="7" r="4"></circle>
          </svg>
        </div>
      </header>

      {/* 탭 & 검색바 */}
      <div className="aus-top-bar">
        <div className="aus-tabs">
          <button 
            className={`aus-tab-btn ${activeTab === 'pending' ? 'active' : ''}`}
            onClick={() => setActiveTab('pending')}
          >
            가입 대기 ({pendingUsers.length})
          </button>
          <button 
            className={`aus-tab-btn ${activeTab === 'list' ? 'active' : ''}`}
            onClick={() => setActiveTab('list')}
          >
            사용자 목록 및 권한 관리 ({approvedUsers.length})
          </button>
        </div>

        <div className="aus-search-box">
          <input 
            type="text" 
            placeholder="이름 또는 사번 검색" 
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          <svg className="aus-search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#999" strokeWidth="2">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
        </div>
      </div>

      {/* TAB 1: 가입 대기 화면 */}
      {activeTab === 'pending' && (
        <div className="aus-tab-content">
          <div className="aus-section-intro">
            <h2>가입 승인 대기 목록</h2>
            <p>가입을 신청한 사용자의 승인을 진행하세요.</p>
          </div>

          <div className="aus-pending-list">
            {filteredPending.length === 0 ? (
              <div className="aus-empty-state">가입 승인 대기 중인 사용자가 없습니다.</div>
            ) : (
              filteredPending.map((user) => (
                <div key={user.id} className="aus-pending-card">
                  <div className="user-info-group">
                    <div className="avatar-circle">
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#718096" strokeWidth="1.5">
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                        <circle cx="12" cy="7" r="4"></circle>
                      </svg>
                    </div>
                    <div className="user-details">
                      <span className="user-name">{user.name}</span>
                      <div className="user-meta">
                        <span>사번 {user.employeeId}</span>
                        <span className="meta-divider"></span>
                        <span>가입 신청일 {user.createdAt}</span>
                      </div>
                    </div>
                  </div>

                  <div className="pending-actions">
                    <button className="btn-approve" onClick={() => handleOpenApproveModal(user)}>승인</button>
                    <button className="btn-reject" onClick={() => handleReject(user.id, user.name)}>거절</button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* TAB 2: 사용자 목록 및 권한 관리 화면 */}
      {activeTab === 'list' && (
        <div className="aus-tab-content">
          <div className="aus-card">
            <div className="aus-card-header">
              <h2 className="aus-sub-title">전체 사용자 관리</h2>
              
              <div className="aus-filter-group">
                <select 
                  className="aus-select-filter"
                  value={selectedPlantFilter}
                  onChange={(e) => setSelectedPlantFilter(e.target.value)}
                >
                  <option value="ALL">전체 발전소 필터</option>
                  {windFarms.map((windFarm) => (
                    <option
                      key={windFarm.id}
                      value={windFarm.name}
                    >
                      {windFarm.name}
                    </option>
                  ))}
                </select>

                <select 
                  className="aus-select-filter"
                  value={sortOrder}
                  onChange={(e) => setSortOrder(e.target.value)}
                >
                  <option value="asc">이름순 ▲</option>
                  <option value="desc">이름순 ▼</option>
                </select>
              </div>
            </div>

            <div className="aus-table-wrapper">
              <table className="aus-table">
                <thead>
                  <tr>
                    <th style={{ width: '12%' }}>이름</th>
                    <th style={{ width: '14%' }}>사번</th>
                    <th style={{ width: '14%' }}>접속 상태</th>
                    <th style={{ width: '36%' }}>담당 발전소</th>
                    <th style={{ width: '24%' }}>제어</th>
                  </tr>
                </thead>
                <tbody>
                  {processedApprovedUsers.length === 0 ? (
                    <tr>
                      <td colSpan="5" className="aus-empty-td">조건에 일치하는 사용자가 없습니다.</td>
                    </tr>
                  ) : (
                    processedApprovedUsers.map((user) => {
                      const firstTwo = user.plants.slice(0, 2);
                      const extraCount = user.plants.length - 2;

                      return (
                        <tr key={user.id}>
                          <td className="font-bold">{user.name}</td>
                          <td className="text-secondary">{user.employeeId}</td>
                          <td>
                            <span className={`status-badge ${user.isOnline ? 'online' : 'offline'}`}>
                              <span className="dot"></span>
                              {user.isOnline ? '온라인' : '오프라인'}
                            </span>
                          </td>
                          <td>
                            <div className="plant-select-container">
                              <div className="plant-tags-box" onClick={() => setOpenDropdownId(openDropdownId === user.id ? null : user.id)}>
                                {user.plants.length === 0 ? (
                                  <span className="plant-placeholder">발전소 선택</span>
                                ) : (
                                  <>
                                    {firstTwo.map((plant, idx) => (
                                      <span key={idx} className="plant-tag">{plant}</span>
                                    ))}
                                    {extraCount > 0 && <span className="plant-badge">+{extraCount}</span>}
                                  </>
                                )}
                                <span className="arrow-icon">∨</span>
                              </div>

                              {openDropdownId === user.id && (
                                <div className="plant-dropdown-menu">
                                  {windFarms.map(
                                    (windFarm) => (
                                      <label
                                        key={windFarm.id}
                                        className="plant-checkbox-item"
                                      >
                                        <input
                                          type="checkbox"
                                          checked={(
                                            user.assignments || []
                                          ).some(
                                            (assignment) =>
                                              assignment.wind_farm_id ===
                                              windFarm.id
                                          )}
                                          onChange={() =>
                                            handleTogglePlantInList(
                                              user.id,
                                              windFarm
                                            )
                                          }
                                        />

                                        <span>
                                          {windFarm.name}
                                        </span>
                                      </label>
                                    )
                                  )}
                                </div>
                              )}
                            </div>
                          </td>
                          <td>
                            <div className="action-cell">
                              <button 
                                className="btn-logout"
                                onClick={() => handleForceLogout(user.id, user.name)}
                              >
                                로그아웃
                              </button>

                              {user.isBlocked ? (
                                <button className="btn-unblock" onClick={() => handleUnblockUser(user.id, user.name)}>
                                  차단 해제
                                </button>
                              ) : (
                                <span className="text-normal">정상</span>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className="aus-footer">
            <div className="total-count">전체 {processedApprovedUsers.length}명</div>
            <div className="pagination">
              <button className="page-nav">&lt;</button>
              <button className="page-num active">1</button>
              <button className="page-num">2</button>
              <button className="page-nav">&gt;</button>
            </div>
          </div>
        </div>
      )}

      {/* 담당 발전소 지정 모달 */}
      {selectedUserForApproval && (
        <div className="modal-overlay">
          <div className="modal-content">
            <h3 className="modal-title">담당 발전소 지정</h3>
            <p className="modal-desc">
              <strong>{selectedUserForApproval.name}</strong> ({selectedUserForApproval.employeeId}) 님이 담당할 발전소를 선택해 주세요.
            </p>

            <div className="modal-plant-grid">
              {windFarms.map((windFarm) => {
                const checked =
                  selectedPlants.includes(
                    windFarm.id
                  );

                return (
                  <label
                    key={windFarm.id}
                    className={`modal-plant-item ${
                      checked ? "selected" : ""
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() =>
                        handleTogglePlantInModal(
                          windFarm.id
                        )
                      }
                    />

                    <span>
                      {windFarm.name}
                    </span>
                  </label>
                );
              })}
            </div>

            <div className="modal-actions">
              <button className="btn-modal-cancel" onClick={handleCloseModal}>취소</button>
              <button className="btn-modal-confirm" onClick={handleConfirmApproval}>승인 완료</button>
            </div>
          </div>
        </div>
      )}

      {/* 에러 메시지 노출 모달 */}
      {errorMessageModal && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ textAlign: 'center', width: '360px' }}>
            <h3 className="modal-title">알림</h3>
            <p className="modal-desc" style={{ margin: '16px 0 24px' }}>{errorMessageModal}</p>
            <button 
              className="btn-modal-confirm" 
              style={{ width: '100%' }}
              onClick={() => setErrorMessageModal(null)}
            >
              확인
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
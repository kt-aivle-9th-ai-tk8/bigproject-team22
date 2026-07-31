import React, { useState } from 'react';
import './AdminUserScreen.css';

// 샘플 발전소 목록
const PLANT_OPTIONS = [
  '장흥 발전소', '해남 발전소', '강진 발전소', '삼천포 발전소', 
  '여수 발전소', '광양 발전소', '태안 발전소', '당진 발전소', 
  '평택 발전소', '보령 발전소', '서천 발전소', '제주 발전소', 
  '서귀포 발전소', '신안 발전소', '영광 발전소', '함평 발전소'
];

// 1. 가입 대기 사용자 샘플 데이터
const INITIAL_PENDING_USERS = [
  { id: 'p1', name: '김민수', employeeId: '2505001', createdAt: '2024.05.20 14:30', plants: ['장흥 발전소'] },
  { id: 'p2', name: '이영희', employeeId: '2505002', createdAt: '2024.05.20 10:15', plants: ['해남 발전소'] },
  { id: 'p3', name: '박지훈', employeeId: '2505003', createdAt: '2024.05.19 16:45', plants: ['삼천포 발전소'] },
  { id: 'p4', name: '최유리', employeeId: '2505004', createdAt: '2024.05.19 11:20', plants: ['보령 발전소'] },
  { id: 'p5', name: '정태호', employeeId: '2505005', createdAt: '2024.05.18 15:05', plants: ['여수 발전소'] },
];

// 2. 승인된 전체 사용자 샘플 데이터
const INITIAL_APPROVED_USERS = [
  { id: 1, name: '최유리', employeeId: '2401001', isOnline: true, plants: ['장흥 발전소', '해남 발전소', '강진 발전소'], isBlocked: true },
  { id: 2, name: '정태호', employeeId: '2401002', isOnline: true, plants: ['해남 발전소', '강진 발전소'], isBlocked: true },
  { id: 3, name: '오세훈', employeeId: '2401003', isOnline: false, plants: ['삼천포 발전소'], isBlocked: false },
  { id: 4, name: '강하나', employeeId: '2401004', isOnline: true, plants: ['여수 발전소', '광양 발전소', '태안 발전소'], isBlocked: true },
  { id: 5, name: '조민석', employeeId: '2401005', isOnline: false, plants: ['태안 발전소'], isBlocked: false },
  { id: 6, name: '김민수', employeeId: '2401006', isOnline: true, plants: ['당진 발전소', '평택 발전소'], isBlocked: true },
  { id: 7, name: '이영희', employeeId: '2401007', isOnline: false, plants: ['보령 발전소', '서천 발전소', '당진 발전소', '평택 발전소'], isBlocked: false },
  { id: 8, name: '박지훈', employeeId: '2401008', isOnline: true, plants: ['제주 발전소', '서귀포 발전소'], isBlocked: true },
  { id: 9, name: '한지민', employeeId: '2401009', isOnline: false, plants: ['신안 발전소'], isBlocked: false },
  { id: 10, name: '윤대영', employeeId: '2401010', isOnline: true, plants: ['영광 발전소', '함평 발전소', '신안 발전소'], isBlocked: true },
];

export default function AdminUserScreen() {
  const [activeTab, setActiveTab] = useState('pending'); // 'pending' | 'list'
  const [pendingUsers, setPendingUsers] = useState(INITIAL_PENDING_USERS);
  const [approvedUsers, setApprovedUsers] = useState(INITIAL_APPROVED_USERS);
  const [searchTerm, setSearchTerm] = useState('');
  const [openDropdownId, setOpenDropdownId] = useState(null);

  // 드롭다운 토글
  const toggleDropdown = (id) => {
    setOpenDropdownId(openDropdownId === id ? null : id);
  };

  // 발전소 선택/해제 공통 함수
  const handleTogglePlant = (userId, plantName, isPending = false) => {
    const updateFn = (list) => list.map(u => {
      if (u.id === userId) {
        const exists = u.plants.includes(plantName);
        const updated = exists 
          ? u.plants.filter(p => p !== plantName)
          : [...u.plants, plantName];
        return { ...u, plants: updated };
      }
      return u;
    });

    if (isPending) {
      setPendingUsers(updateFn);
    } else {
      setApprovedUsers(updateFn);
    }
  };

  // 가입 승인 처리
  const handleApprove = (user) => {
    if (user.plants.length === 0) {
      alert('최소 하나 이상의 담당 발전소를 지정해야 합니다.');
      return;
    }
    alert(`${user.name} 님의 가입이 승인되었습니다.`);
    setPendingUsers(prev => prev.filter(u => u.id !== user.id));
    
    // 승인 목록으로 이동
    const newUser = {
      id: Date.now(),
      name: user.name,
      employeeId: user.employeeId,
      isOnline: false,
      plants: user.plants,
      isBlocked: false,
    };
    setApprovedUsers(prev => [newUser, ...prev]);
  };

  // 가입 거절 처리
  const handleReject = (id, name) => {
    if (window.confirm(`${name} 님의 가입 신청을 거절하시겠습니까?`)) {
      setPendingUsers(prev => prev.filter(u => u.id !== id));
    }
  };

  // 차단 토글
  const handleToggleBlock = (id) => {
    setApprovedUsers(prev => prev.map(u => u.id === id ? { ...u, isBlocked: !u.isBlocked } : u));
  };

  // 강제 로그아웃
  const handleForceLogout = (name) => {
    alert(`${name} 님을 강제 로그아웃 시켰습니다.`);
  };

  // 검색 필터링
  const filteredPending = pendingUsers.filter(u => 
    u.name.includes(searchTerm) || u.employeeId.includes(searchTerm)
  );
  const filteredApproved = approvedUsers.filter(u => 
    u.name.includes(searchTerm) || u.employeeId.includes(searchTerm)
  );

  return (
    <div className="aus-container">
      {/* 상단 헤더 영역 */}
      <header className="aus-header">
        <h1 className="aus-title">사용자 관리</h1>
        <div className="aus-profile-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#666" strokeWidth="1.5">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
            <circle cx="12" cy="7" r="4"></circle>
          </svg>
        </div>
      </header>

      {/* 탭 & 검색창 바 */}
      <div className="aus-top-bar">
        <div className="aus-tabs">
          <button 
            className={`aus-tab-btn ${activeTab === 'pending' ? 'active' : ''}`}
            onClick={() => { setActiveTab('pending'); setOpenDropdownId(null); }}
          >
            가입 대기 ({pendingUsers.length})
          </button>
          <button 
            className={`aus-tab-btn ${activeTab === 'list' ? 'active' : ''}`}
            onClick={() => { setActiveTab('list'); setOpenDropdownId(null); }}
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
              filteredPending.map((user) => {
                const firstTwo = user.plants.slice(0, 2);
                const extraCount = user.plants.length - 2;

                return (
                  <div key={user.id} className="aus-pending-card">
                    {/* 프로필 및 사용자 정보 */}
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

                    {/* 담당 발전소 지정 (다중 선택 셀렉트) */}
                    <div className="plant-assign-group">
                      <label className="assign-label">담당 발전소 지정</label>
                      <div className="plant-select-container">
                        <div className="plant-tags-box" onClick={() => toggleDropdown(user.id)}>
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

                        {/* 드롭다운 메뉴 */}
                        {openDropdownId === user.id && (
                          <div className="plant-dropdown-menu">
                            {PLANT_OPTIONS.map((plantOption) => {
                              const checked = user.plants.includes(plantOption);
                              return (
                                <label key={plantOption} className="plant-checkbox-item">
                                  <input 
                                    type="checkbox" 
                                    checked={checked} 
                                    onChange={() => handleTogglePlant(user.id, plantOption, true)}
                                  />
                                  <span>{plantOption}</span>
                                </label>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* 승인 / 거절 버튼 */}
                    <div className="pending-actions">
                      <button className="btn-approve" onClick={() => handleApprove(user)}>승인</button>
                      <button className="btn-reject" onClick={() => handleReject(user.id, user.name)}>거절</button>
                    </div>
                  </div>
                );
              })
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
            </div>

            <div className="aus-table-wrapper">
              <table className="aus-table">
                <thead>
                  <tr>
                    <th style={{ width: '12%' }}>이름</th>
                    <th style={{ width: '14%' }}>사번</th>
                    <th style={{ width: '14%' }}>접속 상태</th>
                    <th style={{ width: '38%' }}>담당 발전소</th>
                    <th style={{ width: '22%' }}>제어</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredApproved.length === 0 ? (
                    <tr>
                      <td colSpan="5" className="aus-empty-td">조건에 일치하는 사용자가 없습니다.</td>
                    </tr>
                  ) : (
                    filteredApproved.map((user) => {
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
                            {/* 발전소 다중 선택 셀렉트 */}
                            <div className="plant-select-container">
                              <div className="plant-tags-box" onClick={() => toggleDropdown(user.id)}>
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

                              {/* 드롭다운 메뉴 */}
                              {openDropdownId === user.id && (
                                <div className="plant-dropdown-menu">
                                  {PLANT_OPTIONS.map((plantOption) => {
                                    const checked = user.plants.includes(plantOption);
                                    return (
                                      <label key={plantOption} className="plant-checkbox-item">
                                        <input 
                                          type="checkbox" 
                                          checked={checked} 
                                          onChange={() => handleTogglePlant(user.id, plantOption, false)}
                                        />
                                        <span>{plantOption}</span>
                                      </label>
                                    );
                                  })}
                                </div>
                              )}
                            </div>
                          </td>
                          <td>
                            <div className="action-cell">
                              <button 
                                className="btn-logout"
                                onClick={() => handleForceLogout(user.name)}
                              >
                                로그아웃
                              </button>
                              <div className="block-toggle">
                                <span className="toggle-label">차단</span>
                                <label className="switch">
                                  <input 
                                    type="checkbox" 
                                    checked={user.isBlocked} 
                                    onChange={() => handleToggleBlock(user.id)}
                                  />
                                  <span className="slider round"></span>
                                </label>
                              </div>
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

          {/* 하단 푸터 (전체 인원 + 페이지네이션) */}
          <div className="aus-footer">
            <div className="total-count">전체 {filteredApproved.length}명</div>
            <div className="pagination">
              <button className="page-nav">&lt;</button>
              <button className="page-num active">1</button>
              <button className="page-num">2</button>
              <button className="page-nav">&gt;</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
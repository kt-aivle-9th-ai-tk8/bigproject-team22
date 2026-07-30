import React, { useState } from 'react';
import { Search, User, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';
import './AdminUserScreen.css'; // CSS 파일 임포트

const AdminUserScreen = () => {
  const [activeTab, setActiveTab] = useState('users');
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(1);

  const [pendingUsers, setPendingUsers] = useState([
    { id: 1, name: '김민수', employeeId: '2505001', requestDate: '2024.05.20 14:30', plant: '장흥 발전소' },
    { id: 2, name: '이영희', employeeId: '2505002', requestDate: '2024.05.20 10:15', plant: '해남 발전소' },
    { id: 3, name: '박지훈', employeeId: '2505003', requestDate: '2024.05.19 16:45', plant: '삼천포 발전소' },
  ]);

  const [activeUsers, setActiveUsers] = useState([
    { id: 1, name: '최유리', employeeId: '2401001', status: 'online', plants: ['장흥 발전소', '해남 발전소'], extraPlantCount: 1, isBlocked: false },
    { id: 2, name: '정태호', employeeId: '2401002', status: 'online', plants: ['해남 발전소', '강진 발전소'], extraPlantCount: 0, isBlocked: false },
    { id: 3, name: '오세훈', employeeId: '2401003', status: 'offline', plants: ['삼천포 발전소'], extraPlantCount: 0, isBlocked: true },
    { id: 4, name: '강하나', employeeId: '2401004', status: 'online', plants: ['여수 발전소', '광양 발전소'], extraPlantCount: 1, isBlocked: false },
    { id: 5, name: '조민석', employeeId: '2401005', status: 'offline', plants: ['태안 발전소'], extraPlantCount: 0, isBlocked: true },
  ]);

  const handleToggleBlock = (userId) => {
    setActiveUsers(activeUsers.map(u => u.id === userId ? { ...u, isBlocked: !u.isBlocked } : u));
  };

  return (
    <div className="admin-container">
      <div className="admin-card">
        
        {/* Header */}
        <div className="admin-header">
          <h1 className="admin-title">사용자 관리</h1>
          <div className="profile-avatar"><User size={22} /></div>
        </div>

        {/* Tab Navigation */}
        <div className="tab-bar">
          <button
            onClick={() => setActiveTab('pending')}
            className={`tab-button ${activeTab === 'pending' ? 'active' : ''}`}
          >
            가입 대기 ({pendingUsers.length})
          </button>
          <button
            onClick={() => setActiveTab('users')}
            className={`tab-button ${activeTab === 'users' ? 'active' : ''}`}
          >
            사용자 목록 및 권한 관리 ({activeUsers.length})
          </button>
        </div>

        {/* Sub Header & Search */}
        <div className="sub-header">
          <div>
            <h2 className="sub-title">
              {activeTab === 'pending' ? '가입 승인 대기 목록' : '전체 사용자 관리'}
            </h2>
            {activeTab === 'pending' && <p className="sub-desc">가입을 신청한 사용자의 승인을 진행하세요.</p>}
          </div>

          <div className="search-box">
            <input
              type="text"
              placeholder="이름 또는 사번 검색"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="search-input"
            />
            <Search size={18} className="search-icon" />
          </div>
        </div>

        {/* TAB: 사용자 목록 및 권한 관리 */}
        {activeTab === 'users' && (
          <div className="table-wrapper">
            <table className="custom-table">
              <thead>
                <tr>
                  <th>이름</th>
                  <th>사번</th>
                  <th>접속 상태</th>
                  <th>담당 발전소</th>
                  <th style={{ textAlign: 'center' }}>제어</th>
                </tr>
              </thead>
              <tbody>
                {activeUsers.map((user) => {
                  const isOnline = user.status === 'online';
                  return (
                    <tr key={user.id}>
                      <td style={{ fontWeight: 600 }}>{user.name}</td>
                      <td style={{ color: '#64748b' }}>{user.employeeId}</td>
                      <td>
                        <div className="status-badge">
                          <span className={`dot ${isOnline ? 'online' : 'offline'}`} />
                          <span style={{ color: isOnline ? '#334155' : '#94a3b8' }}>
                            {isOnline ? '온라인' : '오프라인'}
                          </span>
                        </div>
                      </td>
                      <td>
                        <div className="plant-badge-group">
                          {user.plants.map((p, idx) => (
                            <span key={idx} className="plant-tag">{p}</span>
                          ))}
                          {user.extraPlantCount > 0 && (
                            <span className="extra-count">+{user.extraPlantCount}</span>
                          )}
                          <ChevronDown size={14} color="#94a3b8" />
                        </div>
                      </td>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '1rem' }}>
                          <button disabled={!isOnline} className="btn-logout">로그아웃</button>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                            <span style={{ fontSize: '0.75rem', color: '#475569' }}>차단</span>
                            <button
                              onClick={() => handleToggleBlock(user.id)}
                              className={`toggle-switch ${!user.isBlocked ? 'active' : ''}`}
                            >
                              <span className="toggle-circle" />
                            </button>
                          </div>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {/* Pagination */}
            <div className="pagination">
              <div>전체 12명</div>
              <div style={{ display: 'flex', gap: '0.25rem', alignItems: 'center' }}>
                <button className="page-btn"><ChevronLeft size={14} /></button>
                <button onClick={() => setCurrentPage(1)} className={`page-btn ${currentPage === 1 ? 'active' : ''}`}>1</button>
                <button onClick={() => setCurrentPage(2)} className={`page-btn ${currentPage === 2 ? 'active' : ''}`}>2</button>
                <button className="page-btn"><ChevronRight size={14} /></button>
              </div>
            </div>
          </div>
        )}

      </div>
    </div>
  );
};

export default AdminUserScreen;
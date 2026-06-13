import { useState, useEffect } from 'react';
import keycloak from './keycloak';
import api from './api';
import DepartmentManager from './components/DepartmentManager';
import './App.css';

interface UserDto {
  id?: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  password?: string;
  roles?: string[];
  phoneNumbers?: string[];
}

function App() {
  const [activeTab, setActiveTab] = useState<'users' | 'departments'>('users');
  const [users, setUsers] = useState<UserDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [syncLoading, setSyncLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Search & filter
  const [phoneSearchQuery, setPhoneSearchQuery] = useState('');
  const [searchMethod, setSearchMethod] = useState<'indexed' | 'like'>('indexed');
  const [responseTime, setResponseTime] = useState<number | null>(null);

  // Pagination states
  const [pageIndex, setPageIndex] = useState(0);
  const [isLastPage, setIsLastPage] = useState(true);

  // Modals state
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserDto | null>(null);

  // Form states
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    firstName: '',
    lastName: '',
    password: '',
    roles: 'USER',
    phoneNumbers: '',
  });

  const [passwordData, setPasswordData] = useState({
    oldPassword: '',
    newPassword: '',
  });

  // Extract current user info from Token
  const currentUsername = keycloak.tokenParsed?.preferred_username || 'User';
  const currentUserEmail = keycloak.tokenParsed?.email || 'N/A';
  const currentUserRoles: string[] = (keycloak.tokenParsed?.realm_access as any)?.roles || [];
  const isAdmin = currentUserRoles.includes('ADMIN');

  useEffect(() => {
    fetchUsers(0, '');
  }, []);

  const fetchUsers = async (page = 0, phoneFilter = phoneSearchQuery, method = searchMethod) => {
    setLoading(true);
    setError(null);
    const startTime = performance.now();
    try {
      const params: any = {
        pageIndex: page,
        pageSize: 10,
      };

      if (phoneFilter.trim()) {
        if (method === 'indexed') {
          params.phoneNumberIndex = phoneFilter.trim();
        } else {
          params.phoneNumber = phoneFilter.trim();
        }
      }

      const response = await api.get('/api/users', { params });
      const duration = performance.now() - startTime;
      setResponseTime(Math.round(duration));

      const pageData = response.data;
      setUsers(pageData.content || []);
      setIsLastPage(pageData.last !== undefined ? pageData.last : true);
      setPageIndex(page);
    } catch (err: any) {
      console.error(err);
      const errMsg = err.response?.data?.message || 'Không thể tải danh sách người dùng.';
      setError(errMsg);
      setResponseTime(null);
    } finally {
      setLoading(false);
    }
  };

  const handleSearchMethodChange = (method: 'indexed' | 'like') => {
    setSearchMethod(method);
    fetchUsers(0, phoneSearchQuery, method);
  };

  const handleSyncAll = async () => {
    setSyncLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const response = await api.post('/api/users/sync-all');
      setSuccess(response.data?.message || 'Đồng bộ hóa người dùng thành công!');
      fetchUsers();
    } catch (err: any) {
      console.error(err);
      setError(err.response?.data?.message || 'Đồng bộ hóa thất bại.');
    } finally {
      setSyncLoading(false);
    }
  };

  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    try {
      const payload: UserDto = {
        username: formData.username,
        email: formData.email,
        firstName: formData.firstName,
        lastName: formData.lastName,
        password: formData.password,
        roles: formData.roles.split(',').map((r) => r.trim().toUpperCase()).filter(Boolean),
        phoneNumbers: formData.phoneNumbers.split(',').map((p) => p.trim()).filter(Boolean),
      };
      await api.post('/api/users', payload);
      setSuccess(`Tạo thành công người dùng: ${formData.username}`);
      setShowAddModal(false);
      resetForm();
      fetchUsers(0);
    } catch (err: any) {
      console.error(err);
      setError(err.response?.data?.message || 'Tạo người dùng thất bại.');
    }
  };

  const handleUpdateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedUser?.id) return;
    setError(null);
    setSuccess(null);
    try {
      const payload: UserDto = {
        username: formData.username,
        email: formData.email,
        firstName: formData.firstName,
        lastName: formData.lastName,
        roles: formData.roles.split(',').map((r) => r.trim().toUpperCase()).filter(Boolean),
        phoneNumbers: formData.phoneNumbers.split(',').map((p) => p.trim()).filter(Boolean),
      };
      if (formData.password) {
        payload.password = formData.password;
      }
      await api.put(`/api/users/${selectedUser.id}`, payload);
      setSuccess(`Cập nhật thành công người dùng: ${formData.username}`);
      setShowEditModal(false);
      resetForm();
      fetchUsers(pageIndex);
    } catch (err: any) {
      console.error(err);
      setError(err.response?.data?.message || 'Cập nhật người dùng thất bại.');
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedUser) return;
    setError(null);
    setSuccess(null);
    try {
      const usernameOrId = selectedUser.id || '';
      await api.put(`/api/users/${usernameOrId}/change-password`, {
        oldPassword: passwordData.oldPassword,
        newPassword: passwordData.newPassword,
      });
      setSuccess('Đổi mật khẩu thành công!');
      setShowPasswordModal(false);
      setPasswordData({ oldPassword: '', newPassword: '' });
    } catch (err: any) {
      console.error(err);
      setError(err.response?.data?.message || 'Đổi mật khẩu thất bại.');
    }
  };

  const handleDeleteUser = async (id: string, username: string) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa người dùng "${username}"?`)) return;
    setError(null);
    setSuccess(null);
    try {
      await api.delete(`/api/users/${id}`);
      setSuccess(`Đã xóa người dùng: ${username}`);
      fetchUsers(0);
    } catch (err: any) {
      console.error(err);
      setError(err.response?.data?.message || 'Xóa người dùng thất bại.');
    }
  };

  const openEditModal = (user: UserDto) => {
    setSelectedUser(user);
    setFormData({
      username: user.username,
      email: user.email || '',
      firstName: user.firstName || '',
      lastName: user.lastName || '',
      password: '',
      roles: user.roles?.join(', ') || 'USER',
      phoneNumbers: user.phoneNumbers?.join(', ') || '',
    });
    setShowEditModal(true);
  };

  const openPasswordModal = (user: UserDto) => {
    setSelectedUser(user);
    setPasswordData({ oldPassword: '', newPassword: '' });
    setShowPasswordModal(true);
  };

  const resetForm = () => {
    setFormData({
      username: '',
      email: '',
      firstName: '',
      lastName: '',
      password: '',
      roles: 'USER',
      phoneNumbers: '',
    });
    setSelectedUser(null);
  };

  const handleLogout = () => {
    keycloak.logout();
  };

  // Filter users based on query
  const filteredUsers = users;

  return (
    <div className="dashboard-container">
      {/* Header */}
      <header className="dashboard-header">
        <div className="header-brand">
          <div className="pulse-dot"></div>
          <h1>Keycloak Admin Portal</h1>
        </div>
        <div className="header-tabs" style={{ display: 'flex', gap: '10px', marginLeft: '20px' }}>
          <button 
            className={`tab-btn ${activeTab === 'users' ? 'active' : ''}`}
            onClick={() => setActiveTab('users')}
            style={{
              padding: '8px 16px',
              borderRadius: '6px',
              border: 'none',
              background: activeTab === 'users' ? 'rgba(59, 130, 246, 0.2)' : 'transparent',
              color: activeTab === 'users' ? '#3b82f6' : '#9ca3af',
              cursor: 'pointer',
              fontWeight: 600,
              transition: 'all 0.2s'
            }}
          >
            Quản lý Người dùng
          </button>
          <button 
            className={`tab-btn ${activeTab === 'departments' ? 'active' : ''}`}
            onClick={() => setActiveTab('departments')}
            style={{
              padding: '8px 16px',
              borderRadius: '6px',
              border: 'none',
              background: activeTab === 'departments' ? 'rgba(59, 130, 246, 0.2)' : 'transparent',
              color: activeTab === 'departments' ? '#3b82f6' : '#9ca3af',
              cursor: 'pointer',
              fontWeight: 600,
              transition: 'all 0.2s'
            }}
          >
            Quản lý Phòng ban
          </button>
        </div>
        <div className="user-profile">
          <div className="avatar">
            {currentUsername.charAt(0).toUpperCase()}
          </div>
          <div className="profile-details">
            <span className="username">{currentUsername}</span>
            <span className="email">{currentUserEmail}</span>
            <div className="badges">
              {currentUserRoles.filter(r => !r.startsWith('default-roles-')).slice(0, 3).map((role) => (
                <span key={role} className={`role-badge ${role === 'ADMIN' ? 'admin' : ''}`}>
                  {role}
                </span>
              ))}
            </div>
          </div>
          <button className="logout-btn" onClick={handleLogout}>
            Đăng xuất
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="dashboard-content">
        {/* Banner Alert Messages */}
        {error && (
          <div className="alert alert-error">
            <svg className="alert-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <div className="alert-content">
              <h4>Đã xảy ra lỗi</h4>
              <p>{error}</p>
            </div>
            <button className="alert-close" onClick={() => setError(null)}>&times;</button>
          </div>
        )}

        {success && (
          <div className="alert alert-success">
            <svg className="alert-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <div className="alert-content">
              <h4>Thành công</h4>
              <p>{success}</p>
            </div>
            <button className="alert-close" onClick={() => setSuccess(null)}>&times;</button>
          </div>
        )}

        {activeTab === 'users' ? (
          <>
            {/* Dashboard Actions Panel */}
            <section className="controls-panel">
          <div className="search-bar">
            <svg className="search-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 5a2 2 0 012-2h3.28a1 1 0 01.94.725l.548 2.2a1 1 0 01-.321.988l-1.305.98a10.582 10.582 0 004.872 4.872l.98-1.305a1 1 0 01.988-.321l2.2.548a1 1 0 01.725.94V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
            </svg>
            <input
              type="text"
              placeholder="Tìm theo số điện thoại (Nhấn Enter)..."
              value={phoneSearchQuery}
              onChange={(e) => setPhoneSearchQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  fetchUsers(0, e.currentTarget.value);
                }
              }}
            />
          </div>

          <div className="search-method-container" style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '4px', backgroundColor: 'rgba(255, 255, 255, 0.03)', borderRadius: '8px', border: '1px solid rgba(255, 255, 255, 0.05)', flexWrap: 'wrap' }}>
            <span style={{ fontSize: '0.8rem', color: '#9ca3af', paddingLeft: '8px' }}>Kiểu tìm:</span>
            <button
              onClick={() => handleSearchMethodChange('indexed')}
              className={`method-btn ${searchMethod === 'indexed' ? 'active' : ''}`}
              style={{
                cursor: 'pointer',
                padding: '6px 12px',
                borderRadius: '6px',
                fontSize: '0.8rem',
                fontWeight: 500,
                backgroundColor: searchMethod === 'indexed' ? 'rgba(52, 211, 153, 0.15)' : 'transparent',
                color: searchMethod === 'indexed' ? '#34d399' : '#9ca3af',
                border: '1px solid ' + (searchMethod === 'indexed' ? 'rgba(52, 211, 153, 0.3)' : 'transparent'),
                transition: 'all 0.2s',
                outline: 'none'
              }}
            >
              Bảng phụ (Index)
            </button>
            <button
              onClick={() => handleSearchMethodChange('like')}
              className={`method-btn ${searchMethod === 'like' ? 'active' : ''}`}
              style={{
                cursor: 'pointer',
                padding: '6px 12px',
                borderRadius: '6px',
                fontSize: '0.8rem',
                fontWeight: 500,
                backgroundColor: searchMethod === 'like' ? 'rgba(248, 113, 113, 0.15)' : 'transparent',
                color: searchMethod === 'like' ? '#f87171' : '#9ca3af',
                border: '1px solid ' + (searchMethod === 'like' ? 'rgba(248, 113, 113, 0.3)' : 'transparent'),
                transition: 'all 0.2s',
                outline: 'none'
              }}
            >
              LIKE %...% (4 cột)
            </button>
          </div>

          {responseTime !== null && (
            <div className="response-time-badge" style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 14px', borderRadius: '8px', fontSize: '0.8rem', backgroundColor: 'rgba(251, 191, 36, 0.1)', color: '#fbbf24', border: '1px solid rgba(251, 191, 36, 0.2)', fontWeight: 500 }}>
              <svg style={{ width: '14px', height: '14px', fill: 'none', stroke: 'currentColor' }} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>Thời gian phản hồi: <strong style={{ fontSize: '0.9rem', color: '#fbbf24' }}>{responseTime} ms</strong></span>
            </div>
          )}

          <div className="action-buttons">
            <button
              className="sync-btn"
              onClick={handleSyncAll}
              disabled={syncLoading || !isAdmin}
            >
              {syncLoading ? (
                <>
                  <span className="spinner"></span>
                  Đang đồng bộ...
                </>
              ) : (
                <>
                  <svg className="btn-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 1121.21 7.89H18" />
                  </svg>
                  Đồng bộ MySQL
                </>
              )}
            </button>

            <button
              className="add-btn"
              onClick={() => { resetForm(); setShowAddModal(true); }}
              disabled={!isAdmin}
            >
              <svg className="btn-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
              </svg>
              Thêm User
            </button>
          </div>
        </section>

        {/* Users Table / List */}
        <section className="table-container">
          {loading ? (
            <div className="table-loading">
              <span className="spinner large"></span>
              <p>Đang tải danh sách người dùng...</p>
            </div>
          ) : filteredUsers.length === 0 ? (
            <div className="empty-state">
              <svg className="empty-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
              <h3>Không tìm thấy người dùng nào</h3>
              <p>Thử tìm kiếm với từ khóa khác hoặc làm mới lại bảng.</p>
              <button className="retry-btn" onClick={() => fetchUsers(0)}>Tải lại</button>
            </div>
          ) : (
            <>
              <table className="users-table">
                <thead>
                  <tr>
                    <th>Tài khoản</th>
                    <th>Họ & Tên</th>
                    <th>Email</th>
                    <th>Vai trò (Roles)</th>
                    <th>Số điện thoại</th>
                    <th style={{ textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredUsers.map((user) => (
                    <tr key={user.id}>
                      <td>
                        <div className="user-info-cell">
                          <div className="initials-avatar">
                            {user.username.substring(0, 2).toUpperCase()}
                          </div>
                          <div>
                            <div className="cell-username">{user.username}</div>
                            <div className="cell-id">ID: {user.id}</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className="cell-fullname">
                          {user.firstName || user.lastName
                            ? `${user.firstName || ''} ${user.lastName || ''}`.trim()
                            : 'Chưa cập nhật'}
                        </span>
                      </td>
                      <td>
                        <span className="cell-email">{user.email || 'N/A'}</span>
                      </td>
                      <td>
                        <div className="table-badges">
                          {user.roles && user.roles.length > 0 ? (
                            user.roles.map((role) => (
                              <span key={role} className={`role-badge ${role === 'ADMIN' ? 'admin' : 'user'}`}>
                                {role}
                              </span>
                            ))
                          ) : (
                            <span className="role-badge none">None</span>
                          )}
                        </div>
                      </td>
                      <td>
                        <div className="table-badges">
                          {user.phoneNumbers && user.phoneNumbers.length > 0 ? (
                            user.phoneNumbers.map((phone) => (
                              <span key={phone} className="role-badge phone-badge" style={{ backgroundColor: 'rgba(16, 185, 129, 0.15)', color: '#34d399', border: '1px solid rgba(16, 185, 129, 0.3)' }}>
                                {phone}
                              </span>
                            ))
                          ) : (
                            <span className="role-badge none">N/A</span>
                          )}
                        </div>
                      </td>
                      <td>
                        <div className="actions-cell">
                          <button
                            className="action-icon-btn edit"
                            onClick={() => openEditModal(user)}
                            title="Sửa thông tin"
                            disabled={!isAdmin}
                          >
                            Sửa
                          </button>
                          <button
                            className="action-icon-btn password"
                            onClick={() => openPasswordModal(user)}
                            title="Đổi mật khẩu"
                            disabled={!isAdmin}
                          >
                            Mật khẩu
                          </button>
                          <button
                            className="action-icon-btn delete"
                            onClick={() => user.id && handleDeleteUser(user.id, user.username)}
                            title="Xóa"
                            disabled={!isAdmin || user.username === currentUsername}
                          >
                            Xóa
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="pagination-panel" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '15px', marginTop: '20px' }}>
                <button
                  disabled={pageIndex === 0}
                  onClick={() => fetchUsers(pageIndex - 1)}
                  className="page-btn"
                >
                  Trước
                </button>
                <span style={{ fontSize: '0.9rem', color: '#9ca3af', fontWeight: 500 }}>Trang {pageIndex + 1}</span>
                <button
                  disabled={isLastPage}
                  onClick={() => fetchUsers(pageIndex + 1)}
                  className="page-btn"
                >
                  Sau
                </button>
              </div>
            </>
          )}
        </section>
          </>
        ) : (
          <DepartmentManager />
        )}
      </main>

      {/* Add User Modal */}
      {showAddModal && (
        <div className="modal-backdrop">
          <div className="modal-content animate-slide-up">
            <div className="modal-header">
              <h3>Thêm người dùng mới</h3>
              <button className="close-btn" onClick={() => setShowAddModal(false)}>&times;</button>
            </div>
            <form onSubmit={handleCreateUser}>
              <div className="modal-body">
                <div className="form-group">
                  <label>Tên đăng nhập (Username) *</label>
                  <input
                    type="text"
                    required
                    value={formData.username}
                    onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                    placeholder="Nhập tên đăng nhập"
                  />
                </div>
                <div className="form-group">
                  <label>Mật khẩu *</label>
                  <input
                    type="password"
                    required
                    value={formData.password}
                    onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                    placeholder="Nhập mật khẩu ban đầu"
                  />
                </div>
                <div className="form-group">
                  <label>Email</label>
                  <input
                    type="email"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    placeholder="example@email.com"
                  />
                </div>
                <div className="row">
                  <div className="form-group col">
                    <label>Tên (First Name)</label>
                    <input
                      type="text"
                      value={formData.firstName}
                      onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                    />
                  </div>
                  <div className="form-group col">
                    <label>Họ (Last Name)</label>
                    <input
                      type="text"
                      value={formData.lastName}
                      onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                    />
                  </div>
                </div>
                <div className="form-group">
                  <label>Vai trò (Cách nhau bởi dấu phẩy, ví dụ: USER, ADMIN)</label>
                  <input
                    type="text"
                    value={formData.roles}
                    onChange={(e) => setFormData({ ...formData, roles: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label>Số điện thoại (Cách nhau bởi dấu phẩy, ví dụ: 0763433779, 0987654321)</label>
                  <input
                    type="text"
                    value={formData.phoneNumbers}
                    onChange={(e) => setFormData({ ...formData, phoneNumbers: e.target.value })}
                    placeholder="Nhập các số điện thoại"
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="cancel-btn" onClick={() => setShowAddModal(false)}>Hủy</button>
                <button type="submit" className="save-btn">Lưu lại</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit User Modal */}
      {showEditModal && selectedUser && (
        <div className="modal-backdrop">
          <div className="modal-content animate-slide-up">
            <div className="modal-header">
              <h3>Cập nhật thông tin: {selectedUser.username}</h3>
              <button className="close-btn" onClick={() => setShowEditModal(false)}>&times;</button>
            </div>
            <form onSubmit={handleUpdateUser}>
              <div className="modal-body">
                <div className="form-group">
                  <label>Tên đăng nhập (Username)</label>
                  <input
                    type="text"
                    value={formData.username}
                    onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label>Email</label>
                  <input
                    type="email"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  />
                </div>
                <div className="row">
                  <div className="form-group col">
                    <label>Tên (First Name)</label>
                    <input
                      type="text"
                      value={formData.firstName}
                      onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                    />
                  </div>
                  <div className="form-group col">
                    <label>Họ (Last Name)</label>
                    <input
                      type="text"
                      value={formData.lastName}
                      onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                    />
                  </div>
                </div>
                <div className="form-group">
                  <label>Mật khẩu mới (Để trống nếu không đổi)</label>
                  <input
                    type="password"
                    value={formData.password}
                    onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                    placeholder="Nhập mật khẩu mới nếu muốn đặt lại"
                  />
                </div>
                <div className="form-group">
                  <label>Vai trò (Cách nhau bởi dấu phẩy, ví dụ: USER, ADMIN)</label>
                  <input
                    type="text"
                    value={formData.roles}
                    onChange={(e) => setFormData({ ...formData, roles: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label>Số điện thoại (Cách nhau bởi dấu phẩy, ví dụ: 0763433779, 0987654321)</label>
                  <input
                    type="text"
                    value={formData.phoneNumbers}
                    onChange={(e) => setFormData({ ...formData, phoneNumbers: e.target.value })}
                    placeholder="Nhập các số điện thoại"
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="cancel-btn" onClick={() => setShowEditModal(false)}>Hủy</button>
                <button type="submit" className="save-btn">Lưu lại</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Change Password Modal */}
      {showPasswordModal && selectedUser && (
        <div className="modal-backdrop">
          <div className="modal-content animate-slide-up">
            <div className="modal-header">
              <h3>Đổi mật khẩu: {selectedUser.username}</h3>
              <button className="close-btn" onClick={() => setShowPasswordModal(false)}>&times;</button>
            </div>
            <form onSubmit={handleChangePassword}>
              <div className="modal-body">
                <div className="form-group">
                  <label>Mật khẩu cũ (Của tài khoản này) *</label>
                  <input
                    type="password"
                    required
                    value={passwordData.oldPassword}
                    onChange={(e) => setPasswordData({ ...passwordData, oldPassword: e.target.value })}
                    placeholder="Nhập mật khẩu cũ của người dùng này"
                  />
                </div>
                <div className="form-group">
                  <label>Mật khẩu mới *</label>
                  <input
                    type="password"
                    required
                    value={passwordData.newPassword}
                    onChange={(e) => setPasswordData({ ...passwordData, newPassword: e.target.value })}
                    placeholder="Nhập mật khẩu mới"
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="cancel-btn" onClick={() => setShowPasswordModal(false)}>Hủy</button>
                <button type="submit" className="save-btn">Cập nhật</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;

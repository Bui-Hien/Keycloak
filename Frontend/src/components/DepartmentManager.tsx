import React, { useState, useEffect, useCallback, useMemo } from 'react';
import api from '../api';
import keycloak from '../keycloak';
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
  IconButton,
  CircularProgress,
  Paper,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  Snackbar,
  Alert,
  Tooltip
} from '@mui/material';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';

export interface DepartmentDto {
  id?: number;
  name: string;
  code: string;
  description?: string;
  parentId?: number | null;
  mpath?: string;
  children?: DepartmentDto[];
}

export interface FlattenNode {
  node: DepartmentDto;
  level: number;
  hasChildren: boolean;
  isExpanded: boolean;
}

export default function DepartmentManager() {
  const [departments, setDepartments] = useState<DepartmentDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [fakeLoading, setFakeLoading] = useState(false);

  // Alert states
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Search & filter
  const [searchQuery, setSearchQuery] = useState('');
  const [keyword, setKeyword] = useState('');

  // Expand / collapse state (set of department IDs)
  const [expandedNodes, setExpandedNodes] = useState<Set<number>>(new Set());

  // Keep track of which nodes have already loaded their children from API
  const [loadedNodes, setLoadedNodes] = useState<Set<number>>(new Set());

  // Pagination states (only applicable for root nodes when not searching)
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // CRUD Modals
  const [showModal, setShowModal] = useState(false);
  const [modalType, setModalType] = useState<'create' | 'edit'>('create');
  const [selectedNode, setSelectedNode] = useState<DepartmentDto | null>(null);
  const [flatParents, setFlatParents] = useState<DepartmentDto[]>([]);

  // Form state
  const [formData, setFormData] = useState({
    name: '',
    code: '',
    description: '',
    parentId: '' as string | number // string empty or number ID
  });

  const currentUserRoles: string[] = (keycloak.tokenParsed?.realm_access as any)?.roles || [];
  const isAdmin = currentUserRoles.includes('ADMIN');

  // Load parent list for dropdown selection
  const loadFlatParentsList = async () => {
    try {
      // Retrieve departments using keyword 'P' to fetch the whole tree hierarchy
      const response = await api.get('/api/departments', {
        params: {
          keyword: 'P',
          page: 0,
          size: 2000
        }
      });
      const rootList = response.data.content || [];
      const flatList: DepartmentDto[] = [];
      const extractFlat = (nodes: DepartmentDto[]) => {
        nodes.forEach(node => {
          flatList.push(node);
          if (node.children && node.children.length > 0) {
            extractFlat(node.children);
          }
        });
      };
      extractFlat(rootList);
      setFlatParents(flatList);
    } catch (err) {
      console.error('Failed to load parents list for dropdown:', err);
    }
  };

  const fetchDepartments = useCallback(async (pageNum = page, searchKeyword = keyword) => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const params: any = {
        page: pageNum,
        size: 10
      };

      if (searchKeyword.trim()) {
        params.keyword = searchKeyword.trim();
      }

      const response = await api.get('/api/departments', { params });
      const pageData = response.data;

      const rootNodes = pageData.content || [];
      setDepartments(rootNodes);
      setTotalPages(pageData.totalPages || 0);
      setTotalElements(pageData.totalElements || 0);
      setPage(pageNum);
      setLoadedNodes(new Set()); // Reset loaded nodes on new page payload

      if (searchKeyword.trim()) {
        // Auto-expand all nodes that have children in the search results
        const allIds = new Set<number>();
        const collectIdsWithChildren = (nodes: DepartmentDto[]) => {
          nodes.forEach(n => {
            if (n.id && n.children && n.children.length > 0) {
              allIds.add(n.id);
              collectIdsWithChildren(n.children);
            }
          });
        };
        collectIdsWithChildren(rootNodes);
        setExpandedNodes(allIds);
      } else {
        // Lazy loading / Root mode: reset expansions when changing page / resetting
        setExpandedNodes(new Set());
      }
    } catch (err: any) {
      console.error(err);
      setErrorMsg(err.response?.data?.message || 'Không thể tải danh sách phòng ban.');
    } finally {
      setLoading(false);
    }
  }, [page, keyword]);

  useEffect(() => {
    fetchDepartments(0, keyword);
  }, [keyword]);

  // Recursively update a node's children inside tree state
  const updateNodeChildren = (nodes: DepartmentDto[], targetId: number, children: DepartmentDto[]): DepartmentDto[] => {
    return nodes.map(node => {
      if (node.id === targetId) {
        return { ...node, children };
      }
      if (node.children && node.children.length > 0) {
        return {
          ...node,
          children: updateNodeChildren(node.children, targetId, children)
        };
      }
      return node;
    });
  };

  // Toggle expand/collapse. Fetches child departments from backend if expanding a node with unloaded children
  const toggleNode = async (node: DepartmentDto) => {
    if (!node.id) return;

    const isExpanded = expandedNodes.has(node.id);
    const newExpanded = new Set(expandedNodes);

    if (isExpanded) {
      newExpanded.delete(node.id);
      setExpandedNodes(newExpanded);
    } else {
      // Fetch children if they are not loaded, or if they are empty and we haven't loaded them explicitly yet
      const hasNotLoaded = !node.children || (!loadedNodes.has(node.id) && node.children.length === 0);

      if (hasNotLoaded) {
        setLoading(true);
        try {
          const response = await api.get('/api/departments', {
            params: {
              parentId: node.id,
              page: 0,
              size: 10
            }
          });
          const childrenData = response.data.content || [];
          setDepartments(prev => updateNodeChildren(prev, node.id!, childrenData));

          const newLoaded = new Set(loadedNodes);
          newLoaded.add(node.id);
          setLoadedNodes(newLoaded);
        } catch (err: any) {
          console.error(err);
          setErrorMsg('Không thể tải danh sách phòng ban con.');
        } finally {
          setLoading(false);
        }
      }
      newExpanded.add(node.id);
      setExpandedNodes(newExpanded);
    }
  };

  // Flatten the tree structure for rendering in MUI Table row-by-row
  const flattenTree = useCallback((nodes: DepartmentDto[], level = 0, list: FlattenNode[] = []) => {
    nodes.forEach(node => {
      if (!node) return;
      const isExpanded = node.id ? expandedNodes.has(node.id) : false;
      // A node has children if its children list is not empty, 
      // or if it has not been lazy-loaded yet (children is undefined), 
      // or if we are searching (meaning backend built trees) and this node is a leaf in the search results but we haven't loaded its real children yet.
      let hasChildren = false;
      if (node.children) {
        if (node.children.length > 0) {
          hasChildren = true;
        } else if (node.id && !loadedNodes.has(node.id) && keyword.trim()) {
          hasChildren = true;
        }
      } else {
        hasChildren = true;
      }

      list.push({
        node,
        level,
        hasChildren,
        isExpanded
      });

      if (node.children && node.children.length > 0 && isExpanded) {
        flattenTree(node.children, level + 1, list);
      }
    });
    return list;
  }, [expandedNodes, loadedNodes, keyword]);

  const displayList = useMemo(() => flattenTree(departments), [departments, flattenTree]);

  // Handle mock data generation
  const handleGenerateFakeData = async () => {
    setFakeLoading(true);
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      await api.post('/api/departments/fake-data');
      setSuccessMsg('Sinh 10.000 phòng ban mẫu thành công!');
      fetchDepartments(0, '');
    } catch (err: any) {
      console.error(err);
      setErrorMsg(err.response?.data?.message || 'Sinh dữ liệu mẫu thất bại.');
    } finally {
      setFakeLoading(false);
    }
  };

  const handleOpenCreateModal = () => {
    setModalType('create');
    setSelectedNode(null);
    setFormData({
      name: '',
      code: '',
      description: '',
      parentId: ''
    });
    loadFlatParentsList();
    setShowModal(true);
  };

  const handleOpenEditModal = (node: DepartmentDto) => {
    setModalType('edit');
    setSelectedNode(node);
    setFormData({
      name: node.name,
      code: node.code,
      description: node.description || '',
      parentId: node.parentId || ''
    });
    loadFlatParentsList();
    setShowModal(true);
  };

  const handleSaveDepartment = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      const payload: DepartmentDto = {
        name: formData.name,
        code: formData.code,
        description: formData.description,
        parentId: formData.parentId ? Number(formData.parentId) : null
      };

      if (modalType === 'edit' && selectedNode?.id) {
        payload.id = selectedNode.id;
        // Check to prevent setting self as parent
        if (payload.parentId === selectedNode.id) {
          setErrorMsg('Không thể chọn chính phòng ban đó làm phòng ban cha.');
          return;
        }
      }

      await api.post('/api/departments', payload);
      setSuccessMsg(modalType === 'create' ? 'Tạo phòng ban thành công!' : 'Cập nhật phòng ban thành công!');
      setShowModal(false);
      fetchDepartments(0, keyword);
    } catch (err: any) {
      console.error(err);
      setErrorMsg(err.response?.data?.message || 'Lưu thông tin phòng ban thất bại.');
    }
  };

  const handleDeleteDepartment = async (id: number, name: string) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa phòng ban "${name}"? Các phòng ban con có thể bị ảnh hưởng.`)) return;
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      await api.delete(`/api/departments/${id}`);
      setSuccessMsg(`Đã xóa phòng ban: ${name}`);
      fetchDepartments(0, keyword);
    } catch (err: any) {
      console.error(err);
      setErrorMsg(err.response?.data?.message || 'Xóa phòng ban thất bại.');
    }
  };

  return (
    <Box sx={{ p: 3, color: '#f3f4f6' }}>
      {/* Title & Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Typography variant="h5" component="h2" sx={{ fontWeight: 600, color: '#fff' }}>
          Quản lý phòng ban (Tree View)
        </Typography>
        <Box sx={{ display: 'flex', gap: 2 }}>
          <Button
            variant="contained"
            color="warning"
            onClick={handleGenerateFakeData}
            disabled={fakeLoading || !isAdmin}
            startIcon={fakeLoading ? <CircularProgress size={20} color="inherit" /> : <RefreshIcon />}
            sx={{ textTransform: 'none', backgroundColor: '#fbbf24', '&:hover': { backgroundColor: '#d97706' } }}
          >
            {fakeLoading ? 'Đang tạo dữ liệu...' : 'Tạo 10.000 PB Mẫu (5 Cấp)'}
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={handleOpenCreateModal}
            disabled={!isAdmin}
            startIcon={<AddIcon />}
            sx={{ textTransform: 'none', backgroundColor: '#3b82f6', '&:hover': { backgroundColor: '#2563eb' } }}
          >
            Thêm Phòng Ban
          </Button>
        </Box>
      </Box>

      {/* Search Bar */}
      <Box sx={{ display: 'flex', gap: 2, mb: 3, backgroundColor: 'rgba(255, 255, 255, 0.02)', p: 2, borderRadius: '8px', border: '1px solid rgba(255, 255, 255, 0.05)' }}>
        <TextField
          variant="outlined"
          size="small"
          placeholder="Tìm theo tên hoặc mã phòng ban..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              setKeyword(searchQuery);
            }
          }}
          sx={{
            flexGrow: 1,
            input: { color: '#fff' },
            '& .MuiOutlinedInput-root': {
              '& fieldset': { borderColor: 'rgba(255, 255, 255, 0.1)' },
              '&:hover fieldset': { borderColor: 'rgba(255, 255, 255, 0.3)' },
              '&.Mui-focused fieldset': { borderColor: '#3b82f6' }
            }
          }}
        />
        <Button
          variant="outlined"
          onClick={() => setKeyword(searchQuery)}
          startIcon={<SearchIcon />}
          sx={{ textTransform: 'none', color: '#9ca3af', borderColor: 'rgba(255, 255, 255, 0.1)', '&:hover': { borderColor: '#fff', color: '#fff' } }}
        >
          Tìm kiếm
        </Button>
        {keyword && (
          <Button
            variant="text"
            onClick={() => { setSearchQuery(''); setKeyword(''); }}
            sx={{ textTransform: 'none', color: '#f87171' }}
          >
            Xóa lọc
          </Button>
        )}
      </Box>

      {/* Main Table */}
      <TableContainer component={Paper} sx={{ backgroundColor: 'rgba(30, 41, 59, 0.7)', backdropFilter: 'blur(10px)', border: '1px solid rgba(255, 255, 255, 0.05)', borderRadius: '12px', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)' }}>
        {loading && departments.length === 0 ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', p: 5 }}>
            <CircularProgress color="primary" />
            <Typography sx={{ mt: 2, color: '#9ca3af' }}>Đang tải sơ đồ phòng ban...</Typography>
          </Box>
        ) : displayList.length === 0 ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', p: 5 }}>
            <Typography sx={{ color: '#9ca3af', mb: 2 }}>Không tìm thấy phòng ban nào.</Typography>
            <Button variant="outlined" onClick={() => fetchDepartments(0, '')} size="small" sx={{ color: '#fff', borderColor: 'rgba(255, 255, 255, 0.2)' }}>Tải lại</Button>
          </Box>
        ) : (
          <Table aria-label="department tree table">
            <TableHead sx={{ backgroundColor: 'rgba(15, 23, 42, 0.5)' }}>
              <TableRow>
                <TableCell sx={{ color: '#9ca3af', fontWeight: 600, borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>Tên phòng ban</TableCell>
                <TableCell sx={{ color: '#9ca3af', fontWeight: 600, borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>Mã phòng ban</TableCell>
                <TableCell sx={{ color: '#9ca3af', fontWeight: 600, borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>Mô tả</TableCell>
                <TableCell sx={{ color: '#9ca3af', fontWeight: 600, borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>Đường dẫn (mpath)</TableCell>
                <TableCell align="right" sx={{ color: '#9ca3af', fontWeight: 600, borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {displayList.map((item: FlattenNode) => {
                const { node, level, hasChildren, isExpanded } = item;
                return (
                  <TableRow
                    key={node.id}
                    sx={{
                      '&:hover': { backgroundColor: 'rgba(255, 255, 255, 0.03)' },
                      transition: 'background-color 0.2s'
                    }}
                  >
                    <TableCell
                      sx={{
                        color: '#fff',
                        borderBottom: '1px solid rgba(255, 255, 255, 0.05)',
                        paddingLeft: `${level * 32 + 16}px`, // Thụt lề thụ động theo level phòng ban
                        display: 'flex',
                        alignItems: 'center'
                      }}
                    >
                      {hasChildren ? (
                        <IconButton
                          size="small"
                          onClick={() => toggleNode(node)}
                          sx={{ color: '#9ca3af', mr: 1, p: 0.5 }}
                        >
                          {isExpanded ? <KeyboardArrowDownIcon /> : <KeyboardArrowRightIcon />}
                        </IconButton>
                      ) : (
                        <Box sx={{ width: 28, height: 28, display: 'inline-block' }} />
                      )}
                      <Typography sx={{ fontSize: '0.95rem', fontWeight: level === 0 ? 600 : 400 }}>
                        {node.name}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ color: '#cbd5e1', borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>
                      <code>{node.code}</code>
                    </TableCell>
                    <TableCell sx={{ color: '#9ca3af', borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>
                      {node.description || '—'}
                    </TableCell>
                    <TableCell sx={{ color: '#64748b', borderBottom: '1px solid rgba(255, 255, 255, 0.05)', fontFamily: 'monospace', fontSize: '0.85rem' }}>
                      {node.mpath}
                    </TableCell>
                    <TableCell align="right" sx={{ borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>
                      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
                        <Tooltip title="Sửa thông tin">
                          <IconButton
                            size="small"
                            onClick={() => handleOpenEditModal(node)}
                            disabled={!isAdmin}
                            sx={{ color: '#3b82f6', '&.Mui-disabled': { color: 'rgba(59, 130, 246, 0.3)' } }}
                          >
                            <EditIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Xóa phòng ban">
                          <IconButton
                            size="small"
                            onClick={() => node.id && handleDeleteDepartment(node.id, node.name)}
                            disabled={!isAdmin}
                            sx={{ color: '#ef4444', '&.Mui-disabled': { color: 'rgba(239, 68, 68, 0.3)' } }}
                          >
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}

        {/* Pagination Panel (shown when totalPages > 1) */}
        {totalPages > 1 && (
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', p: 3, gap: 2, borderTop: '1px solid rgba(255, 255, 255, 0.05)' }}>
            <Button
              disabled={page === 0 || loading}
              onClick={() => fetchDepartments(page - 1)}
              variant="outlined"
              size="small"
              sx={{ color: '#fff', borderColor: 'rgba(255, 255, 255, 0.1)', '&:disabled': { color: '#4b5563', borderColor: 'rgba(255,255,255,0.03)' } }}
            >
              Trước
            </Button>
            <Typography sx={{ color: '#9ca3af', fontSize: '0.9rem' }}>
              Trang {page + 1} / {totalPages} (Tổng: {totalElements} nút gốc)
            </Typography>
            <Button
              disabled={page >= totalPages - 1 || loading}
              onClick={() => fetchDepartments(page + 1)}
              variant="outlined"
              size="small"
              sx={{ color: '#fff', borderColor: 'rgba(255, 255, 255, 0.1)', '&:disabled': { color: '#4b5563', borderColor: 'rgba(255,255,255,0.03)' } }}
            >
              Sau
            </Button>
          </Box>
        )}
      </TableContainer>

      {/* CRUD Dialog Modal */}
      <Dialog
        open={showModal}
        onClose={() => setShowModal(false)}
        maxWidth="sm"
        fullWidth
        slotProps={{
          paper: {
            sx: {
              backgroundColor: '#1e293b',
              color: '#fff',
              border: '1px solid rgba(255, 255, 255, 0.05)',
              borderRadius: '12px'
            }
          }
        }}
      >
        <form onSubmit={handleSaveDepartment}>
          <DialogTitle sx={{ borderBottom: '1px solid rgba(255, 255, 255, 0.05)', fontWeight: 600 }}>
            {modalType === 'create' ? 'Thêm phòng ban mới' : `Sửa phòng ban: ${selectedNode?.name}`}
          </DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 2 }}>
            <TextField
              label="Tên phòng ban *"
              variant="outlined"
              size="small"
              required
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              fullWidth
              slotProps={{
                inputLabel: { style: { color: '#9ca3af' } },
                input: { style: { color: '#fff' } }
              }}
              sx={{ '& .MuiOutlinedInput-root': { '& fieldset': { borderColor: 'rgba(255, 255, 255, 0.1)' } } }}
            />
            <TextField
              label="Mã phòng ban *"
              variant="outlined"
              size="small"
              required
              disabled={modalType === 'edit'} // Lock code during edits
              value={formData.code}
              onChange={(e) => setFormData({ ...formData, code: e.target.value })}
              fullWidth
              slotProps={{
                inputLabel: { style: { color: '#9ca3af' } },
                input: { style: { color: '#fff' } }
              }}
              sx={{ '& .MuiOutlinedInput-root': { '& fieldset': { borderColor: 'rgba(255, 255, 255, 0.1)' } } }}
            />
            <TextField
              label="Mô tả"
              variant="outlined"
              size="small"
              multiline
              rows={3}
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              fullWidth
              slotProps={{
                inputLabel: { style: { color: '#9ca3af' } },
                input: { style: { color: '#fff' } }
              }}
              sx={{ '& .MuiOutlinedInput-root': { '& fieldset': { borderColor: 'rgba(255, 255, 255, 0.1)' } } }}
            />
            <FormControl fullWidth size="small">
              <InputLabel id="parent-select-label" sx={{ color: '#9ca3af' }}>Phòng ban cha (Bỏ trống nếu là gốc)</InputLabel>
              <Select
                labelId="parent-select-label"
                id="parent-select"
                value={formData.parentId}
                label="Phòng ban cha (Bỏ trống nếu là gốc)"
                onChange={(e) => setFormData({ ...formData, parentId: e.target.value })}
                sx={{
                  color: '#fff',
                  '& .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255, 255, 255, 0.1)' },
                  '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: 'rgba(255, 255, 255, 0.3)' },
                  '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#3b82f6' },
                  '& .MuiSvgIcon-root': { color: '#9ca3af' }
                }}
                MenuProps={{
                  slotProps: {
                    paper: {
                      sx: {
                        backgroundColor: '#1e293b',
                        color: '#fff',
                        maxHeight: 300,
                        border: '1px solid rgba(255, 255, 255, 0.1)'
                      }
                    }
                  }
                }}
              >
                <MenuItem value=""><em>Không chọn (Nút gốc)</em></MenuItem>
                {flatParents
                  .filter(p => modalType === 'create' || p.id !== selectedNode?.id) // Filter out self during edit
                  .map((parent) => (
                    <MenuItem key={parent.id} value={parent.id!}>
                      {parent.name} ({parent.code})
                    </MenuItem>
                  ))}
              </Select>
            </FormControl>
          </DialogContent>
          <DialogActions sx={{ p: 2.5, borderTop: '1px solid rgba(255, 255, 255, 0.05)' }}>
            <Button onClick={() => setShowModal(false)} sx={{ color: '#9ca3af', textTransform: 'none' }}>
              Hủy
            </Button>
            <Button type="submit" variant="contained" sx={{ backgroundColor: '#3b82f6', textTransform: 'none', '&:hover': { backgroundColor: '#2563eb' } }}>
              Lưu lại
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* Messages Alert Popups */}
      <Snackbar open={!!successMsg} autoHideDuration={4000} onClose={() => setSuccessMsg(null)}>
        <Alert severity="success" variant="filled" sx={{ width: '100%' }}>
          {successMsg}
        </Alert>
      </Snackbar>
      <Snackbar open={!!errorMsg} autoHideDuration={5000} onClose={() => setErrorMsg(null)}>
        <Alert severity="error" variant="filled" sx={{ width: '100%' }}>
          {errorMsg}
        </Alert>
      </Snackbar>
    </Box>
  );
}

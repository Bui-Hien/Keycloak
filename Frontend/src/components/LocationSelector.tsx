import { useState, useEffect } from 'react';
import { FormControl, InputLabel, Select, MenuItem, Box, CircularProgress, Typography, Paper } from '@mui/material';
import api from '../api'; // Axios instance

interface Country {
  id: string;
  name: string;
}

interface AdminLevelSchema {
  level: number;
  levelName: string;
}

interface AdminUnit {
  id: number;
  name: string;
}

interface LocationSelectorProps {
  onSelectFinalUnit: (unitId: number | null, fullText: string) => void;
}

export default function LocationSelector({ onSelectFinalUnit }: LocationSelectorProps) {
  const [countries, setCountries] = useState<Country[]>([]);
  const [selectedCountry, setSelectedCountry] = useState<string>('');
  
  const [levelsSchema, setLevelsSchema] = useState<AdminLevelSchema[]>([]);
  const [selections, setSelections] = useState<Record<number, number>>({}); // { level: selected_unit_id }
  const [options, setOptions] = useState<Record<number, AdminUnit[]>>({});   // { level: [units] }
  
  const [loading, setLoading] = useState(false);

  // 1. Fetch countries on mount
  useEffect(() => {
    setLoading(true);
    api.get('/api/countries')
      .then(res => setCountries(res.data))
      .catch(err => console.error("Error fetching countries:", err))
      .finally(() => setLoading(false));
  }, []);

  // helper to construct full text representation of selections
  const getFullAddressText = (currentSelections: Record<number, number>) => {
    const parts: string[] = [];
    
    // Sort levels to build address from lowest to highest or vice versa
    const sortedLevels = [...levelsSchema].sort((a, b) => b.level - a.level);
    
    sortedLevels.forEach(schema => {
      const selectedId = currentSelections[schema.level];
      if (selectedId) {
        const unit = options[schema.level]?.find(opt => opt.id === selectedId);
        if (unit) {
          parts.push(unit.name);
        }
      }
    });

    const countryObj = countries.find(c => c.id === selectedCountry);
    if (countryObj) {
      parts.push(countryObj.name);
    }

    return parts.join(', ');
  };

  // 2. Handle Country selection
  const handleCountryChange = async (countryId: string) => {
    setSelectedCountry(countryId);
    setSelections({});
    setOptions({});
    setLevelsSchema([]);
    onSelectFinalUnit(null, '');

    if (!countryId) return;

    setLoading(true);
    try {
      // Fetch level schemas for this country
      const schemaRes = await api.get(`/api/countries/${countryId}/levels`);
      const schema: AdminLevelSchema[] = schemaRes.data;
      setLevelsSchema(schema);

      if (schema.length > 0) {
        // Fetch Level 1 data (no parentId)
        const level1Res = await api.get('/api/administrative-units', {
          params: { countryId, level: 1 }
        });
        setOptions({ 1: level1Res.data });
      }
    } catch (err) {
      console.error("Error loading country schema:", err);
    } finally {
      setLoading(false);
    }
  };

  // 3. Handle Dropdown value change at level L
  const handleLevelChange = async (level: number, unitId: number) => {
    if (!unitId) {
      // If empty choice selected, clear selections from current level downwards
      const newSelections = { ...selections };
      levelsSchema.forEach(s => {
        if (s.level >= level) {
          delete newSelections[s.level];
        }
      });
      setSelections(newSelections);
      onSelectFinalUnit(null, '');
      return;
    }

    const newSelections = { ...selections, [level]: unitId };
    
    // Clear selections and option lists for lower levels (> level)
    levelsSchema.forEach(s => {
      if (s.level > level) {
        delete newSelections[s.level];
        delete options[s.level];
      }
    });
    
    setSelections(newSelections);

    const nextLevelSchema = levelsSchema.find(s => s.level === level + 1);
    
    if (nextLevelSchema) {
      setLoading(true);
      try {
        const res = await api.get('/api/administrative-units', {
          params: {
            countryId: selectedCountry,
            level: level + 1,
            parentId: unitId
          }
        });
        setOptions(prev => ({ ...prev, [level + 1]: res.data }));
      } catch (err) {
        console.error("Error loading next level units:", err);
      } finally {
        setLoading(false);
      }
      // Since there is a next level, we haven't selected the final unit yet
      onSelectFinalUnit(null, '');
    } else {
      // This was the lowest level, invoke callback with the selected unit ID and full text
      const fullText = getFullAddressText(newSelections);
      onSelectFinalUnit(unitId, fullText);
    }
  };

  return (
    <Paper 
      elevation={0} 
      sx={{ 
        p: 3, 
        backgroundColor: '#ffffff', 
        border: '1px solid #e2e8f0', 
        borderRadius: '12px',
        boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.05), 0 2px 4px -2px rgb(0 0 0 / 0.05)',
        maxWidth: 500,
        width: '100%'
      }}
    >
      <Typography variant="subtitle1" sx={{ color: '#0f172a', fontWeight: 600, mb: 2 }}>
        Chọn Địa chỉ Đa Quốc gia (Dynamic Schema)
      </Typography>

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
        {/* 1. Country Selection */}
        <FormControl fullWidth size="small">
          <InputLabel id="country-select-label" sx={{ color: '#64748b' }}>Quốc gia</InputLabel>
          <Select
            labelId="country-select-label"
            id="country-select"
            value={selectedCountry}
            label="Quốc gia"
            onChange={(e) => handleCountryChange(e.target.value)}
            disabled={loading}
            sx={{
              color: '#0f172a',
              '& .MuiOutlinedInput-notchedOutline': { borderColor: '#cbd5e1' },
              '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: '#94a3b8' },
              '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#3b82f6' },
              '& .MuiSvgIcon-root': { color: '#64748b' }
            }}
            MenuProps={{
              slotProps: {
                paper: {
                  sx: {
                    backgroundColor: '#ffffff',
                    color: '#0f172a',
                    border: '1px solid #e2e8f0'
                  }
                }
              }
            }}
          >
            <MenuItem value=""><em>Chọn quốc gia</em></MenuItem>
            {countries.map(c => (
              <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>
            ))}
          </Select>
        </FormControl>

        {/* 2. Loading Spinner */}
        {loading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', my: 1 }}>
            <CircularProgress size={24} color="primary" />
          </Box>
        )}

        {/* 3. Dynamic Administrative Levels Dropdowns */}
        {selectedCountry && levelsSchema.map((schema) => {
          const currentLevel = schema.level;
          const prevLevel = currentLevel - 1;
          const isParentSelected = currentLevel === 1 || !!selections[prevLevel];
          const levelOptions = options[currentLevel] || [];

          return (
            <FormControl key={currentLevel} fullWidth size="small" disabled={loading || !isParentSelected}>
              <InputLabel id={`label-level-${currentLevel}`} sx={{ color: isParentSelected ? '#64748b' : 'rgba(100, 116, 139, 0.4)' }}>{schema.levelName}</InputLabel>
              <Select
                labelId={`label-level-${currentLevel}`}
                id={`select-level-${currentLevel}`}
                value={selections[currentLevel] || ''}
                label={schema.levelName}
                onChange={(e) => handleLevelChange(currentLevel, Number(e.target.value))}
                disabled={loading || !isParentSelected}
                sx={{
                  color: isParentSelected ? '#0f172a' : 'rgba(15, 23, 42, 0.3)',
                  '& .MuiOutlinedInput-notchedOutline': { borderColor: isParentSelected ? '#cbd5e1' : '#f1f5f9' },
                  '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: isParentSelected ? '#94a3b8' : '#f1f5f9' },
                  '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#3b82f6' },
                  '& .MuiSvgIcon-root': { color: isParentSelected ? '#64748b' : 'rgba(100, 116, 139, 0.4)' }
                }}
                MenuProps={{
                  slotProps: {
                    paper: {
                      sx: {
                        backgroundColor: '#ffffff',
                        color: '#0f172a',
                        maxHeight: 250,
                        border: '1px solid #e2e8f0'
                      }
                    }
                  }
                }}
              >
                <MenuItem value=""><em>Chọn {schema.levelName}</em></MenuItem>
                {levelOptions.map(opt => (
                  <MenuItem key={opt.id} value={opt.id}>{opt.name}</MenuItem>
                ))}
              </Select>
            </FormControl>
          );
        })}
      </Box>
    </Paper>
  );
}

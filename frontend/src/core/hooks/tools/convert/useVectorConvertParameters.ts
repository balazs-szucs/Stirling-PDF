import { BaseParameters } from '@app/types/parameters';
import { useBaseParameters, BaseParametersHook } from '@app/hooks/tools/shared/useBaseParameters';
import { useCallback, useMemo } from 'react';

export interface VectorConvertParameters extends BaseParameters {
  fromExtension: string;
  toExtension: string;
  outputFormat: string;
  prepress: boolean;
}

export interface VectorConvertParametersHook extends BaseParametersHook<VectorConvertParameters> {
  getEndpoint: () => string;
  analyzeFileTypes: (files: Array<{name: string}>) => void;
}

export const defaultParameters: VectorConvertParameters = {
  fromExtension: '',
  toExtension: '',
  outputFormat: 'eps',
  prepress: false,
};

const validateParameters = (params: VectorConvertParameters): boolean => {
  const { fromExtension, toExtension } = params;

  if (!fromExtension || !toExtension) return false;

  if (fromExtension === 'pdf' && ['eps', 'ps', 'pcl', 'xps'].includes(toExtension)) {
    return true;
  } else if (['ps', 'eps', 'epsf'].includes(fromExtension) && toExtension === 'pdf') {
    return true;
  }

  return false;
};

const getEndpointName = (params: VectorConvertParameters): string => {
  const { fromExtension, toExtension } = params;

  if (fromExtension === 'pdf' && ['eps', 'ps', 'pcl', 'xps'].includes(toExtension)) {
    return 'pdf-to-vector';
  } else if (['ps', 'eps', 'epsf'].includes(fromExtension) && toExtension === 'pdf') {
    return 'vector-to-pdf';
  }

  return '';
};

export const useVectorConvertParameters = (): VectorConvertParametersHook => {
  const config = useMemo(() => ({
    defaultParameters,
    endpointName: getEndpointName,
    validateFn: validateParameters,
  }), []);

  const baseHook = useBaseParameters(config);

  const getEndpoint = () => {
    const { fromExtension, toExtension } = baseHook.parameters;

    if (fromExtension === 'pdf' && ['eps', 'ps', 'pcl', 'xps'].includes(toExtension)) {
      return '/api/v1/convert/pdf/vector';
    } else if (['ps', 'eps', 'epsf'].includes(fromExtension) && toExtension === 'pdf') {
      return '/api/v1/convert/vector/pdf';
    }

    return '';
  };

  const analyzeFileTypes = useCallback((files: Array<{name: string}>) => {
    if (files.length === 0) {
      return;
    }

    if (files.length === 1) {
      const fileName = files[0].name.toLowerCase();
      let detectedExt = '';

      if (fileName.endsWith('.pdf')) {
        detectedExt = 'pdf';
      } else if (fileName.endsWith('.ps')) {
        detectedExt = 'ps';
      } else if (fileName.endsWith('.eps')) {
        detectedExt = 'eps';
      } else if (fileName.endsWith('.epsf')) {
        detectedExt = 'epsf';
      }

      if (detectedExt) {
        baseHook.setParameters(prev => {
          let newToExtension = prev.toExtension;

          if (detectedExt === 'pdf' && !prev.toExtension) {
            newToExtension = 'eps'; // Default vector format
          } else if (['ps', 'eps', 'epsf'].includes(detectedExt) && !prev.toExtension) {
            newToExtension = 'pdf';
          }

          return {
            ...prev,
            fromExtension: detectedExt,
            toExtension: newToExtension
          };
        });
      }
    } else {
      const extensions = files.map(file => {
        const fileName = file.name.toLowerCase();
        if (fileName.endsWith('.pdf')) return 'pdf';
        if (fileName.endsWith('.ps')) return 'ps';
        if (fileName.endsWith('.eps')) return 'eps';
        if (fileName.endsWith('.epsf')) return 'epsf';
        return '';
      }).filter(ext => ext !== '');

      const uniqueExtensions = [...new Set(extensions)];

      if (uniqueExtensions.length === 1) {
        const detectedExt = uniqueExtensions[0];
        baseHook.setParameters(prev => {
          let newToExtension = prev.toExtension;

          if (detectedExt === 'pdf' && !prev.toExtension) {
            newToExtension = 'eps'; // Default vector format
          } else if (['ps', 'eps', 'epsf'].includes(detectedExt) && !prev.toExtension) {
            newToExtension = 'pdf';
          }

          return {
            ...prev,
            fromExtension: detectedExt,
            toExtension: newToExtension
          };
        });
      }
    }
  }, [baseHook.setParameters]);

  return {
    ...baseHook,
    getEndpoint,
    analyzeFileTypes,
  };
};

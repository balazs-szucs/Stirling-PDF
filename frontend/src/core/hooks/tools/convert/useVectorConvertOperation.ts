import { useCallback } from 'react';
import apiClient from '@app/services/apiClient';
import { useTranslation } from 'react-i18next';
import { VectorConvertParameters, defaultParameters } from '@app/hooks/tools/convert/useVectorConvertParameters';
import { createFileFromApiResponse } from '@app/utils/fileResponseUtils';
import { useToolOperation, ToolType, CustomProcessorResult } from '@app/hooks/tools/shared/useToolOperation';

export const buildVectorConvertFormData = (parameters: VectorConvertParameters, selectedFiles: File[]): FormData => {
  const formData = new FormData();

  selectedFiles.forEach(file => {
    formData.append("fileInput", file);
  });

  const { fromExtension, toExtension, outputFormat, prepress } = parameters;

  if (fromExtension === 'pdf' && ['eps', 'ps', 'pcl', 'xps'].includes(toExtension)) {
    formData.append("outputFormat", outputFormat);
  } else if (['ps', 'eps', 'epsf'].includes(fromExtension) && toExtension === 'pdf') {
    formData.append("prepress", prepress.toString());
  }

  return formData;
};

export const createVectorFileFromResponse = (
  responseData: any,
  headers: any,
  originalFileName: string,
  targetExtension: string
): File => {
  const originalName = originalFileName.split('.')[0];

  let finalExtension = targetExtension;
  if (targetExtension === 'vector') {
    finalExtension = 'eps'; // Default to eps if vector format is not specified
  }

  const fallbackFilename = `${originalName}.${finalExtension}`;

  return createFileFromApiResponse(responseData, headers, fallbackFilename);
};

export const vectorConvertProcessor = async (
  parameters: VectorConvertParameters,
  selectedFiles: File[]
): Promise<CustomProcessorResult> => {
  const processedFiles: File[] = [];
  const endpoint = parameters.fromExtension === 'pdf'
    ? '/api/v1/convert/pdf/vector'
    : '/api/v1/convert/vector/pdf';

  if (!endpoint) {
    throw new Error('Unsupported conversion format');
  }

  for (const file of selectedFiles) {
    try {
      const formData = buildVectorConvertFormData(parameters, [file]);
      const response = await apiClient.post(endpoint, formData, { responseType: 'blob' });

      const convertedFile = createVectorFileFromResponse(
        response.data,
        response.headers,
        file.name,
        parameters.toExtension
      );

      processedFiles.push(convertedFile);
    } catch (error) {
      console.warn(`Failed to convert file ${file.name}:`, error);
    }
  }

  return {
    files: processedFiles,
    consumedAllInputs: true,
  };
};

export const vectorConvertOperationConfig = {
  toolType: ToolType.custom,
  customProcessor: vectorConvertProcessor, // Can't use callback version here
  operationType: 'vector-convert',
  defaultParameters,
} as const;

export const useVectorConvertOperation = () => {
  const { t } = useTranslation();

  const customVectorConvertProcessor = useCallback(async (
    parameters: VectorConvertParameters,
    selectedFiles: File[]
  ): Promise<CustomProcessorResult> => {
    return vectorConvertProcessor(parameters, selectedFiles);
  }, []);

  return useToolOperation<VectorConvertParameters>({
    ...vectorConvertOperationConfig,
    customProcessor: customVectorConvertProcessor, // Use instance-specific processor for translation support
    getErrorMessage: (error) => {
      if (error.response?.data && typeof error.response.data === 'string') {
        return error.response.data;
      }
      if (error.message) {
        return error.message;
      }
      return t("vectorConvert.errorConversion", "An error occurred while converting the file.");
    },
  });
};

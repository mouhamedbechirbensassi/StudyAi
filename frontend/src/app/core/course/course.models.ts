export type ProcessingStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'NO_TEXT';

export interface Course {
  id: number;
  displayNumber?: number;
  title: string;
  description?: string | null;
  userId?: number;
  createdAt: string;
  updatedAt?: string | null;
  documentsCount?: number;
  hasDocument?: boolean;
  documentFilename?: string | null;
  documentFileSize?: number | null;
  documentUploadedAt?: string | null;
}

export interface CourseRequest {
  title: string;
  description?: string | null;
}

export interface CourseDocument {
  id: number;
  courseId: number;
  originalFilename: string;
  fileSize: number;
  contentType?: string;
  uploadedAt: string;
  extractedText?: string | null;
  processingStatus?: ProcessingStatus;
  processingError?: string | null;
  chunksCount?: number;
}

export interface DocumentChunk {
  id: number;
  documentId: number;
  chunkIndex: number;
  content: string;
  pageNumber?: number | null;
  charCount: number;
  createdAt: string;
}

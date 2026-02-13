import { useEffect, useMemo, useState, useCallback } from 'react';
import { useBookmarkCapability, BookmarkCapability } from '@embedpdf/plugin-bookmark/react';
import { useDocumentManagerCapability } from '@embedpdf/plugin-document-manager/react';
import { useViewer } from '@app/contexts/ViewerContext';
import { BookmarkState, BookmarkAPIWrapper } from '@app/contexts/viewer/viewerBridges';

export function BookmarkAPIBridge() {
  const { provides: bookmarkCapability } = useBookmarkCapability();
  const { provides: documentManagerCapability } = useDocumentManagerCapability();
  const { registerBridge } = useViewer();
  const [state, setState] = useState<BookmarkState>({
    bookmarks: null,
    isLoading: false,
    error: null,
  });
  const [documentReady, setDocumentReady] = useState(false);

  // Wait for document to be ready before making the capability available
  useEffect(() => {
    if (!documentManagerCapability) return;

    const checkDocumentReady = async () => {
      try {
        const activeDoc = documentManagerCapability.getActiveDocument?.();
        if (activeDoc?.id) {
          setDocumentReady(true);
        }
      } catch (_e) {
        // Document not ready yet
      }
    };

    checkDocumentReady();

    // Subscribe to document open events
    const unsubscribe = documentManagerCapability.onDocumentOpened?.((event: any) => {
      if (event?.documentId || event?.id) {
        setDocumentReady(true);
      }
    });

    return () => {
      if (typeof unsubscribe === 'function') {
        unsubscribe();
      }
    };
  }, [documentManagerCapability]);

  const fetchBookmarks = useCallback(
    async (capability: BookmarkCapability) => {
      if (!documentReady) {
        throw new Error('Document not ready');
      }

      setState(prev => ({ ...prev, isLoading: true, error: null }));
      try {
        const task = capability.getBookmarks();
        const result = await task.toPromise();
        setState({
          bookmarks: result.bookmarks ?? [],
          isLoading: false,
          error: null,
        });
        return result.bookmarks ?? [];
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to load bookmarks';
        setState({
          bookmarks: null,
          isLoading: false,
          error: message,
        });
        throw error;
      }
    },
    [documentReady]
  );

  const api = useMemo<BookmarkAPIWrapper | null>(() => {
    // Only provide API when both capability AND document are ready
    if (!bookmarkCapability || !documentReady) return null;

    return {
      fetchBookmarks: () => fetchBookmarks(bookmarkCapability),
      clearBookmarks: () => {
        setState({
          bookmarks: null,
          isLoading: false,
          error: null,
        });
      },
      setLocalBookmarks: (bookmarks, error = null) => {
        setState({
          bookmarks,
          isLoading: false,
          error,
        });
      },
    };
  }, [bookmarkCapability, documentReady, fetchBookmarks]);

  useEffect(() => {
    if (!api) {
      registerBridge('bookmark', null);
      return;
    }

    registerBridge('bookmark', {
      state,
      api,
    });

    return () => {
      registerBridge('bookmark', null);
    };
  }, [api, state, registerBridge]);

  return null;
}

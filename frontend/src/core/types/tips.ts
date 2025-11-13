export interface TooltipTip {
  title?: string;
  description?: string;
  bullets?: string[];
  body?: React.ReactNode;
}

export interface TooltipContent {
  header?: {
    title: string;
    logo?: string | React.ReactNode;
  };
  tips?: TooltipTip[];
  content?: React.ReactNode;
  // Support for named tooltip sections (alternative to tips array)
  [key: string]: any;
}

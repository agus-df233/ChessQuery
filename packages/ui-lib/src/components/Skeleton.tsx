import { HTMLAttributes } from 'react';
import { cn } from '../utils/cn';

export interface SkeletonProps extends HTMLAttributes<HTMLDivElement> {
  width?: number | string;
  height?: number | string;
  rounded?: boolean;
}

export const Skeleton = ({ width, height = 14, rounded, className, style, ...rest }: SkeletonProps) => (
  <div
    className={cn('skeleton', className)}
    style={{
      width: typeof width === 'number' ? `${width}px` : width ?? '100%',
      height: typeof height === 'number' ? `${height}px` : height,
      borderRadius: rounded ? 999 : 6,
      ...style,
    }}
    {...rest}
  />
);

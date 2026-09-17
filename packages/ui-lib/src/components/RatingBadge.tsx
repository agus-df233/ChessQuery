import { Badge } from './Badge';

export interface RatingBadgeProps {
  rating: number;
  label?: string;
}

type Variant = Parameters<typeof Badge>[0]['variant'];

const ratingVariant = (rating: number): { variant: Variant; color: string } => {
  if (rating < 1200) return { variant: 'danger', color: 'var(--red)' };
  if (rating < 1600) return { variant: 'warning', color: 'var(--yellow)' };
  if (rating < 2000) return { variant: 'success', color: 'var(--accent)' };
  if (rating < 2400) return { variant: 'info', color: 'var(--blue)' };
  return { variant: 'gold', color: 'var(--gold)' };
};

export const RatingBadge = ({ rating, label }: RatingBadgeProps) => {
  const { variant } = ratingVariant(rating);
  return (
    <Badge variant={variant}>
      {label && <span style={{ marginRight: 4, opacity: 0.8 }}>{label}</span>}
      <strong>{Math.round(rating)}</strong>
    </Badge>
  );
};

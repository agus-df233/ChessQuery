import { useQuery } from '@tanstack/react-query';
import { usersApi } from './users';

/** Quién soy. Es la consulta más usada (guards, cabecera, dashboard): se cachea 1 minuto. */
export const useMe = () =>
  useQuery({ queryKey: ['me'], queryFn: usersApi.me, staleTime: 60_000 });

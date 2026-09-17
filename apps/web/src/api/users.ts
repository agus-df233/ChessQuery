import { http } from './client';
import type {
  Club, Country, Friend, FriendRequest, FriendshipStatus, Me, Organization, OrganizationRequest, Profile,
  PublicProfile, RankingEntry, RatingPoint, RatingType, RosterCreateRequest, SearchResult, UpdateProfileRequest,
} from './types';

/** Funciones de acceso a la API de users, una por endpoint. Sin lógica: solo tipos y rutas. */
export const usersApi = {
  me: () => http.get<Me>('/api/users/me').then((r) => r.data),
  updateMyProfile: (body: UpdateProfileRequest) => http.put<Profile>('/api/users/me/profile', body).then((r) => r.data),
  myRatingHistory: (type: RatingType = 'NATIONAL', months = 12) =>
    http.get<RatingPoint[]>('/api/users/me/rating-history', { params: { type, months } }).then((r) => r.data),
  syncExternalRatings: () => http.post<Profile>('/api/users/me/external-ratings/sync').then((r) => r.data),
  publicProfile: (id: number) => http.get<PublicProfile>(`/api/users/${id}/public-profile`).then((r) => r.data),
  ratingHistory: (id: number, type: RatingType = 'NATIONAL', months = 12) =>
    http.get<RatingPoint[]>(`/api/users/${id}/rating-history`, { params: { type, months } }).then((r) => r.data),
  search: (q: string, limit = 20) => http.get<SearchResult[]>('/api/users/search', { params: { q, limit } }).then((r) => r.data),
  ranking: (category?: string, region?: string, limit = 50) =>
    http.get<RankingEntry[]>('/api/users/ranking', { params: { category: category || undefined, region: region || undefined, limit } })
        .then((r) => r.data),
  countries: () => http.get<Country[]>('/api/catalog/countries').then((r) => r.data),
  clubs: () => http.get<Club[]>('/api/catalog/clubs').then((r) => r.data),
};

export const organizationsApi = {
  create: (body: OrganizationRequest) => http.post<Organization>('/api/organizations', body).then((r) => r.data),
  mine: () => http.get<Organization>('/api/organizations/me').then((r) => r.data),
  update: (body: OrganizationRequest) => http.put<Organization>('/api/organizations/me', body).then((r) => r.data),
  roster: () => http.get<Profile[]>('/api/organizations/me/roster').then((r) => r.data),
  addToRoster: (body: RosterCreateRequest) => http.post<Profile>('/api/organizations/me/roster', body).then((r) => r.data),
  updateTags: (playerId: number, tags: string[]) =>
    http.patch<Profile>(`/api/organizations/me/roster/${playerId}/tags`, { tags }).then((r) => r.data),
  deactivate: (playerId: number) => http.delete<void>(`/api/organizations/me/roster/${playerId}`).then(() => undefined),
};

export const friendsApi = {
  list: () => http.get<Friend[]>('/api/friends').then((r) => r.data),
  requests: (direction: 'incoming' | 'outgoing') =>
    http.get<FriendRequest[]>('/api/friends/requests', { params: { direction } }).then((r) => r.data),
  request: (addresseeId: number) => http.post<FriendshipStatus>('/api/friends/requests', { addresseeId }).then((r) => r.data),
  accept: (requestId: number) => http.post<FriendshipStatus>(`/api/friends/requests/${requestId}/accept`).then((r) => r.data),
  decline: (requestId: number) => http.post<void>(`/api/friends/requests/${requestId}/decline`).then(() => undefined),
  remove: (otherId: number) => http.delete<void>(`/api/friends/${otherId}`).then(() => undefined),
  status: (otherId: number) => http.get<FriendshipStatus>(`/api/friends/status/${otherId}`).then((r) => r.data),
};

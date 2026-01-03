import { Track } from './types';

class TrackStore {
  private tracks = new Map<string, Track>();
  private clientColors = new Map<string, string>();

  private generateColor(userId: string): string {
    if (this.clientColors.has(userId)) {
      return this.clientColors.get(userId)!;
    }

    const colors = [
      '#FF5722', '#E91E63', '#9C27B0', '#673AB7', '#3F51B5',
      '#2196F3', '#00BCD4', '#009688', '#4CAF50', '#8BC34A',
      '#CDDC39', '#FFC107', '#FF9800', '#FF5722', '#795548'
    ];

    const hash = userId.split('').reduce((acc, char) => {
      return char.charCodeAt(0) + ((acc << 5) - acc);
    }, 0);

    const colorIndex = Math.abs(hash) % colors.length;
    const color = colors[colorIndex];
    this.clientColors.set(userId, color);
    return color;
  }

  saveTrack(track: Track): void {
    const trackWithColor = {
      ...track,
      color: this.generateColor(track.userId)
    };
    this.tracks.set(track.id, trackWithColor);
  }

  getTrack(trackId: string): Track | undefined {
    return this.tracks.get(trackId);
  }

  getActiveTracksByUser(userId: string): Track[] {
    return Array.from(this.tracks.values())
      .filter(track => track.userId === userId && track.isActive);
  }

  getAllActiveTracks(): Track[] {
    return Array.from(this.tracks.values())
      .filter(track => track.isActive);
  }

  updateTrack(trackId: string, updates: Partial<Track>): Track | undefined {
    const track = this.tracks.get(trackId);
    if (!track) return undefined;

    const updatedTrack = {
      ...track,
      ...updates,
      color: track.color // Preserve the color
    };
    this.tracks.set(trackId, updatedTrack);
    return updatedTrack;
  }

  deleteTrack(trackId: string): boolean {
    return this.tracks.delete(trackId);
  }
}

export const trackStore = new TrackStore();


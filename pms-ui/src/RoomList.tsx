import { useEffect, useState } from "react";

// Mirrors your RoomResponse record.
type Room = {
  id: number;
  roomNumber: string;
  type: string;
  status: string;
  rate: number;
};

export default function RoomList({ hotelId }: { hotelId: number }) {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    fetch(`/api/hotels/${hotelId}/rooms`)
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json();
      })
      .then((page) => setRooms(page.content)) // your Page<RoomResponse>
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [hotelId]);

  if (loading) return <p>Loading rooms…</p>;
  if (error) return <p style={{ color: "red" }}>Failed: {error}</p>;

  return (
    <div>
      <h2>Rooms in hotel {hotelId}</h2>
      <table border={1} cellPadding={6}>
        <thead>
          <tr>
            <th>Room</th>
            <th>Type</th>
            <th>Status</th>
            <th>Rate</th>
          </tr>
        </thead>
        <tbody>
          {rooms.map((room) => (
            <tr key={room.id}>
              <td>{room.roomNumber}</td>
              <td>{room.type}</td>
              <td>{room.status}</td>
              <td>{room.rate}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {rooms.length === 0 && <p>No rooms found.</p>}
    </div>
  );
}

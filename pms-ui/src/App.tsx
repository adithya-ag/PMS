import RoomList from "./RoomList";

export default function App() {
  return (
    <div style={{ fontFamily: "sans-serif", padding: 24 }}>
      <h1>Hotel PMS</h1>
      <RoomList hotelId={1} />
    </div>
  );
}

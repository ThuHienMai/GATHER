const rotations = [
  { value: 'America/Los_Angeles', label: 'San Francisco' },
  { value: 'Asia/Tokyo', label: 'Tokyo' },
  { value: 'America/Argentina/Buenos_Aires', label: 'Buenos Aires' },
  { value: 'Europe/Berlin', label: 'Berlin' },
];

export function TimezoneOptions({ current }: { current?: string }) {
  return <>
    {current && !rotations.some(zone => zone.value === current) &&
      <option value={current}>{current}</option>}
    {rotations.map(zone => <option key={zone.value} value={zone.value}>{zone.label}</option>)}
  </>;
}

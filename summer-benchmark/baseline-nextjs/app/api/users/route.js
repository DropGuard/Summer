import { NextResponse } from 'next/server';
import { userMap } from '@/app/store';

export async function POST(request) {
  const body = await request.json();
  userMap.set(body.id, body);
  return NextResponse.json(body);
}

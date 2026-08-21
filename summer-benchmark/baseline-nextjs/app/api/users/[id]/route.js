import { NextResponse } from 'next/server';
import { userMap } from '@/app/store';

export async function GET(request, { params }) {
  const user = userMap.get(params.id);
  if (!user) return new NextResponse(null, { status: 404 });
  return NextResponse.json(user);
}

export async function PUT(request, { params }) {
  const id = params.id;
  if (!userMap.has(id)) return new NextResponse(null, { status: 404 });
  const body = await request.json();
  userMap.set(id, body);
  return NextResponse.json(body);
}

export async function DELETE(request, { params }) {
  userMap.delete(params.id);
  return new NextResponse(null, { status: 200 });
}

'use client';
import { useUser } from '../components/session';
import Landing from '../components/landing';
import HomeFeed from './home/page';
export default function Page(){return useUser()?<HomeFeed/>:<Landing/>;}

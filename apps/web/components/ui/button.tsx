import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva,type VariantProps } from 'class-variance-authority';
import { cn } from '../../lib/utils';
const variants=cva('inline-flex items-center justify-center gap-2 rounded-xl text-sm font-semibold transition-colors focus-visible:outline-2 disabled:opacity-50 disabled:pointer-events-none',{variants:{variant:{default:'bg-emerald-800 text-white hover:bg-emerald-900',outline:'border border-stone-300 bg-transparent hover:bg-stone-100'},size:{default:'h-11 px-5',small:'h-9 px-3'}},defaultVariants:{variant:'default',size:'default'}});
export function Button({className,variant,size,asChild=false,...props}:React.ComponentProps<'button'>&VariantProps<typeof variants>&{asChild?:boolean}){const Component=asChild?Slot:'button';return <Component className={cn(variants({variant,size,className}))} {...props}/>;}

#!/usr/bin/env python3
"""Sprites de luz originales (halo, rayos, estrella de lobulos, haz). Blanco con el brillo en el canal alfa:
se dibujan con mezcla aditiva y se tiñen con el color del vertice."""
import numpy as np, os
from PIL import Image
OUT=os.path.join(os.path.dirname(__file__),'..','src/main/resources/assets/revivalcampfire/textures/effect/')
os.makedirs(OUT,exist_ok=True)
def save(a,name):
    a=np.clip(a,0,1); h,w=a.shape
    rgba=np.zeros((h,w,4),np.uint8); rgba[...,:3]=255; rgba[...,3]=(a*255).astype(np.uint8)
    Image.fromarray(rgba,'RGBA').save(OUT+name)
def grid(n):
    c=(np.arange(n)+0.5)/n*2-1; x,y=np.meshgrid(c,c); return x,y,np.sqrt(x*x+y*y),np.arctan2(y,x)

# halo: nucleo brillante + resplandor suave + un aro tenue
x,y,r,th=grid(128)
halo=np.exp(-(r/0.16)**2)*1.0+np.exp(-(r/0.45)**2)*0.55+0.10*np.exp(-((r-0.78)/0.06)**2)
halo*=np.clip((1-r)/0.2,0,1)
save(halo,'halo.png')

# rayos: abanico de rayos de distinto largo y grosor
rng=np.random.default_rng(5); x,y,r,th=grid(256)
ray=np.zeros_like(r)
for k in range(16):
    ang=k/16*2*np.pi+rng.uniform(-0.12,0.12); w=rng.uniform(0.025,0.07); ln=rng.uniform(0.55,1.0)
    d=np.angle(np.exp(1j*(th-ang)))
    ray=np.maximum(ray,np.exp(-(d/w)**2)*np.clip(1-r/ln,0,1)**1.4)
ray=ray*np.clip(r/0.04,0,1)+np.exp(-(r/0.08)**2)
save(ray*np.clip((1-r)/0.1,0,1),'rays.png')

# estrella de lobulos: cuatro puntas finas + nucleo
x,y,r,th=grid(128)
star=np.exp(-(np.abs(x)*np.abs(y))/0.0025)*np.clip(1-r,0,1)**1.2 + np.exp(-(r/0.13)**2)
save(star*np.clip((1-r)/0.1,0,1),'lobes.png')

# haz vertical: degradado horizontal con nucleo + desvanece arriba y abajo
w,h=32,128; u=(np.arange(w)+0.5)/w*2-1; v=(np.arange(h)+0.5)/h
core=(1-np.abs(u))**1.6; vf=np.clip(v/0.12,0,1)*np.clip((1-v)/0.25,0,1)
beam=np.outer(vf,core)
save(beam,'beam.png')
print('ok')

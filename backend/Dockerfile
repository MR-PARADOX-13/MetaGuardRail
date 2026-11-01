FROM node:20-alpine
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --only=production || npm install --only=production
COPY server.js ./
EXPOSE 3000
CMD ["node", "server.js"]

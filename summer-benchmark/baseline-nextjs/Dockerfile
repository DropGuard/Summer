FROM node:24-alpine
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

ENV HOSTNAME="0.0.0.0"
ENV PORT=3000
ENV NODE_OPTIONS="--max-old-space-size=200"
EXPOSE 3000
CMD ["node", "custom-server.js"]

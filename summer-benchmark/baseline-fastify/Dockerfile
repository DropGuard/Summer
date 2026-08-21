FROM node:24-alpine
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
ENV NODE_OPTIONS="--max-old-space-size=200"
EXPOSE 3000
CMD ["node", "server.js"]

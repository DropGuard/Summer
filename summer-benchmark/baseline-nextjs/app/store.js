export const userMap = new Map();

for (let i = 1; i <= 10; i++) {
  userMap.set(String(i), {
    id: String(i),
    name: "User" + i,
    email: "user" + i + "@example.com"
  });
}

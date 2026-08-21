package main

import (
	"net/http"
	"strconv"
	"sync"

	"github.com/gin-gonic/gin"
)

type User struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Email string `json:"email"`
}

var (
	userMap = make(map[string]User)
	mutex   = &sync.RWMutex{}
)

func init() {
	for i := 1; i <= 10; i++ {
		idStr := strconv.Itoa(i)
		userMap[idStr] = User{
			ID:    idStr,
			Name:  "User" + idStr,
			Email: "user" + idStr + "@example.com",
		}
	}
}

func main() {
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()

	r.GET("/health/live", func(c *gin.Context) {
		c.String(http.StatusOK, "OK")
	})

	r.GET("/users/:id", func(c *gin.Context) {
		id := c.Param("id")
		mutex.RLock()
		user, exists := userMap[id]
		mutex.RUnlock()
		if !exists {
			c.Status(http.StatusNotFound)
			return
		}
		c.JSON(http.StatusOK, user)
	})

	r.POST("/users", func(c *gin.Context) {
		var user User
		if err := c.ShouldBindJSON(&user); err != nil {
			c.Status(http.StatusBadRequest)
			return
		}
		mutex.Lock()
		userMap[user.ID] = user
		mutex.Unlock()
		c.JSON(http.StatusOK, user)
	})

	r.PUT("/users/:id", func(c *gin.Context) {
		id := c.Param("id")
		var user User
		if err := c.ShouldBindJSON(&user); err != nil {
			c.Status(http.StatusBadRequest)
			return
		}
		mutex.Lock()
		if _, exists := userMap[id]; !exists {
			mutex.Unlock()
			c.Status(http.StatusNotFound)
			return
		}
		userMap[id] = user
		mutex.Unlock()
		c.JSON(http.StatusOK, user)
	})

	r.DELETE("/users/:id", func(c *gin.Context) {
		id := c.Param("id")
		mutex.Lock()
		delete(userMap, id)
		mutex.Unlock()
		c.Status(http.StatusOK)
	})

	r.Run(":8080")
}

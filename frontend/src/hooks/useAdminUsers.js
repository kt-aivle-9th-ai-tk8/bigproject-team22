import {
  useCallback,
  useEffect,
  useState,
} from "react";

import {
  fetchAdminUsers,
} from "../api/adminUserApi";

export const useAdminUsers = ({
  role,
} = {}) => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] =
    useState(false);
  const [error, setError] =
    useState(null);

  const loadUsers = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const responseBody =
        await fetchAdminUsers({
          role,
        });

      const rawUsers =
        responseBody?.data?.users ??
        responseBody?.users ??
        [];

      const filteredUsers = role
        ? rawUsers.filter(
            (user) => user.role === role
          )
        : rawUsers.filter(
            (user) =>
              user.role === "ADMIN" ||
              user.role === "MANAGER"
          );

      const userList = filteredUsers.map(
        (user) => ({
          id: user.user_id,
          name: user.user_name,
          employeeId: user.employee_id,

          isOnline: Boolean(
            user.session_active
          ),

          plants: Array.isArray(
            user.assignments
          )
            ? user.assignments.map(
                (assignment) =>
                  assignment.wind_farm_name
              )
            : [],

          assignments: Array.isArray(
            user.assignments
          )
            ? user.assignments
            : [],

          role: user.role,

          createdAt:
            user.created_at || "오늘",

          isBlocked: false,
        })
      );

      setUsers(userList);

      
    } catch (error) {
      

      setError(error.message);
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, [role]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  return {
    users,
    loading,
    error,
    refetch: loadUsers,
  };
};
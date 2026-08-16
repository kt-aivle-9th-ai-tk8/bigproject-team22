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

      const responseData =
        responseBody?.data ??
        responseBody;

      const rawUsers =
        Array.isArray(responseData)
          ? responseData
          : [];

      const userList = rawUsers
        .filter(
          (user) =>
            user.role === "ADMIN" ||
            user.role === "MANAGER"
        )
        .map((user) => ({
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

          isBlocked: false,
        }));

      setUsers(userList);

      console.log(
        "관리자 사용자 목록:",
        userList
      );
    } catch (error) {
      console.error(
        "관리자 사용자 목록 조회 에러:",
        error
      );

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
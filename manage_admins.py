#!/usr/bin/env python3
"""Manage ScoreBoard admin accounts.

Admin accounts are NOT stored in code or environment variables. They are real
Supabase Auth users whose email exists in the public.admins table. Use this CLI
on the server (with SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY set) to add /
remove administrators:

    python manage_admins.py add <email>            # hidden password prompt
    python manage_admins.py add <email> --stdin    # read password from stdin
    python manage_admins.py reset-password <email>
    python manage_admins.py remove <email>
    python manage_admins.py list

There is no mock/dev mode: every command requires a working Supabase
connection and fails closed if it is not configured.
"""
import argparse
import getpass
import sys

from app.app import add_admin as _add, remove_admin as _remove
from app.app import list_admins as _list, supabase_client


def _read_password_stdin():
    return sys.stdin.readline().rstrip("\r\n")


def _prompt_password():
    pw = getpass.getpass("Password for admin (min 12 characters): ")
    confirm = getpass.getpass("Repeat password: ")
    if pw != confirm:
        sys.exit("Error: passwords do not match.")
    return pw


def _collect_password(args):
    password = _read_password_stdin() if args.stdin else _prompt_password()
    if not password:
        sys.exit("Error: empty password.")
    return password


def cmd_add(args):
    password = _collect_password(args)
    try:
        _add(args.email, password)
    except (ValueError, RuntimeError) as e:
        sys.exit(f"Error: {e}")
    print(f"Admin account created/updated for {args.email}")


def cmd_reset_password(args):
    password = _collect_password(args)
    try:
        _add(args.email, password)
        if supabase_client:
            try:
                users = supabase_client.auth.admin.list_users()
                match = next(
                    (u for u in users if (u.email or "").lower() == args.email.lower()),
                    None,
                )
                if match:
                    supabase_client.auth.admin.update_user_by_id(match.id, {"password": password})
                    print(f"Supabase Auth password updated for {args.email}")
                else:
                    print(f"Warning: no Supabase Auth user '{args.email}' found to update.")
            except Exception as e:
                print(f"Note: could not update Supabase Auth password automatically: {e}")
    except (ValueError, RuntimeError) as e:
        sys.exit(f"Error: {e}")
    print(f"Password updated for {args.email}")


def cmd_remove(args):
    try:
        _remove(args.email)
    except RuntimeError as e:
        sys.exit(f"Error: {e}")
    print(f"Admin account removed for {args.email}")


def cmd_list(_):
    admins = _list()
    if not admins:
        print("No admin accounts.")
        return
    for item in admins:
        print(f"  {item.get('email')}  created_at={item.get('created_at')}")


def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p_add = sub.add_parser("add", help="create or update an admin account")
    p_add.add_argument("email")
    p_add.add_argument("--stdin", action="store_true", help="read the password from stdin")
    p_add.set_defaults(func=cmd_add)

    p_reset = sub.add_parser("reset-password", help="set a new password for an admin")
    p_reset.add_argument("email")
    p_reset.add_argument("--stdin", action="store_true", help="read the password from stdin")
    p_reset.set_defaults(func=cmd_reset_password)

    p_rm = sub.add_parser("remove", help="remove an admin account")
    p_rm.add_argument("email")
    p_rm.set_defaults(func=cmd_remove)

    p_ls = sub.add_parser("list", help="list admin accounts")
    p_ls.set_defaults(func=cmd_list)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
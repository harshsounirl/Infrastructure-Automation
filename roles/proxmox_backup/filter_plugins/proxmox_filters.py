#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Custom Jinja2 filters for the proxmox_backup Ansible role."""

from __future__ import absolute_import, division, print_function
__metaclass__ = type


def proxmox_prune_string(retention):
    """Convert a retention dict to a PBS/PVE prune options string.

    Accepts keys: keep_last, keep_hourly, keep_daily, keep_weekly,
                  keep_monthly, keep_yearly  (underscore form from YAML).
    Returns a comma-separated string like 'keep-last=5,keep-daily=7'.
    Keys with value 0 are omitted.
    """
    key_map = [
        ('keep_last',    'keep-last'),
        ('keep_hourly',  'keep-hourly'),
        ('keep_daily',   'keep-daily'),
        ('keep_weekly',  'keep-weekly'),
        ('keep_monthly', 'keep-monthly'),
        ('keep_yearly',  'keep-yearly'),
    ]
    parts = []
    for dict_key, api_key in key_map:
        value = int(retention.get(dict_key, 0))
        if value > 0:
            parts.append('{0}={1}'.format(api_key, value))
    return ','.join(parts)


def proxmox_vmid_list(vmids):
    """Convert a list of VMIDs (int or str) to a comma-separated string."""
    return ','.join(str(v) for v in vmids)


def proxmox_diff_job(existing, desired, keys):
    """Return True if any of the specified keys differ between two dicts."""
    for key in keys:
        if str(existing.get(key, '')) != str(desired.get(key, '')):
            return True
    return False


class FilterModule(object):
    """Jinja2 filter module for Proxmox backup management."""

    def filters(self):
        return {
            'proxmox_prune_string': proxmox_prune_string,
            'proxmox_vmid_list': proxmox_vmid_list,
            'proxmox_diff_job': proxmox_diff_job,
        }
